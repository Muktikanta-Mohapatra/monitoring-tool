use std::cell::UnsafeCell;
use std::mem::MaybeUninit;
use std::sync::atomic::{AtomicU64, AtomicUsize, Ordering};
use std::sync::Arc;

pub trait QueueItem: Send + Sized {
    fn size(&self) -> usize;
}

#[derive(Debug)]
#[repr(align(64))]
struct CacheLinePadded<T> {
    value: T,
}

impl<T> CacheLinePadded<T> {
    fn new(value: T) -> Self {
        CacheLinePadded { value }
    }
}

struct Slot<T> {
    sequence: CacheLinePadded<AtomicU64>,
    data: UnsafeCell<MaybeUninit<T>>,
}

unsafe impl<T: Send> Send for Slot<T> {}
unsafe impl<T: Send> Sync for Slot<T> {}

pub struct LockFreeRingBuffer<T: QueueItem> {
    slots: Box<[Slot<T>]>,
    capacity: usize,
    capacity_mask: usize,
    head: CacheLinePadded<AtomicU64>,
    tail: CacheLinePadded<AtomicU64>,
}

unsafe impl<T: QueueItem> Send for LockFreeRingBuffer<T> {}
unsafe impl<T: QueueItem> Sync for LockFreeRingBuffer<T> {}

impl<T: QueueItem> LockFreeRingBuffer<T> {
    fn new(capacity: usize) -> Self {
        assert!(capacity.is_power_of_two());
        assert!(capacity >= 16);

        let slots = (0..capacity)
            .map(|i| Slot {
                sequence: CacheLinePadded::new(AtomicU64::new(i as u64)),
                data: UnsafeCell::new(MaybeUninit::uninit()),
            })
            .collect::<Vec<_>>()
            .into_boxed_slice();

        LockFreeRingBuffer {
            slots,
            capacity,
            capacity_mask: capacity - 1,
            head: CacheLinePadded::new(AtomicU64::new(0)),
            tail: CacheLinePadded::new(AtomicU64::new(0)),
        }
    }

    #[inline]
    fn push(&self, item: T) -> Result<(), T> {
        let mut pos = self.head.value.load(Ordering::Acquire);
        loop {
            let slot_idx = (pos & self.capacity_mask as u64) as usize;
            let slot = unsafe { &self.slots.get_unchecked(slot_idx) };
            let seq = slot.sequence.value.load(Ordering::Acquire);

            if seq == pos {
                match self.head.value.compare_exchange_weak(
                    pos,
                    pos.wrapping_add(1),
                    Ordering::Release,
                    Ordering::Relaxed,
                ) {
                    Ok(_) => {
                        unsafe {
                            (*slot.data.get()).write(item);
                        }
                        slot.sequence
                            .value
                            .store(pos.wrapping_add(1), Ordering::Release);
                        return Ok(());
                    }
                    Err(new_pos) => {
                        pos = new_pos;
                    }
                }
            } else if seq > pos {
                return Err(item);
            } else {
                pos = self.head.value.load(Ordering::Acquire);
            }
        }
    }

    #[inline]
    fn pop(&self) -> Option<T> {
        let mut pos = self.tail.value.load(Ordering::Acquire);
        loop {
            let slot_idx = (pos & self.capacity_mask as u64) as usize;
            let slot = unsafe { &self.slots.get_unchecked(slot_idx) };
            let seq = slot.sequence.value.load(Ordering::Acquire);

            if seq == pos.wrapping_add(1) {
                match self.tail.value.compare_exchange_weak(
                    pos,
                    pos.wrapping_add(1),
                    Ordering::Release,
                    Ordering::Relaxed,
                ) {
                    Ok(_) => {
                        let item = unsafe { (*slot.data.get()).assume_init_read() };
                        slot.sequence.value.store(
                            pos.wrapping_add(self.capacity as u64 + 1),
                            Ordering::Release,
                        );
                        return Some(item);
                    }
                    Err(new_pos) => {
                        pos = new_pos;
                    }
                }
            } else if seq <= pos {
                return None;
            } else {
                pos = self.tail.value.load(Ordering::Acquire);
            }
        }
    }

    #[inline]
    fn len(&self) -> usize {
        let head = self.head.value.load(Ordering::Acquire);
        let tail = self.tail.value.load(Ordering::Acquire);
        ((head - tail) % (self.capacity as u64 * 2)) as usize
    }

    #[inline]
    fn is_empty(&self) -> bool {
        self.head.value.load(Ordering::Acquire) == self.tail.value.load(Ordering::Acquire)
    }

    #[inline]
    fn is_full(&self) -> bool {
        let head = self.head.value.load(Ordering::Acquire);
        let tail = self.tail.value.load(Ordering::Acquire);
        head.wrapping_sub(tail) >= self.capacity as u64
    }

    #[inline]
    fn capacity(&self) -> usize {
        self.capacity
    }
}

impl<T: QueueItem> Drop for LockFreeRingBuffer<T> {
    fn drop(&mut self) {
        while self.pop().is_some() {}
    }
}

pub struct EventQueue<T: QueueItem> {
    buffer: Arc<LockFreeRingBuffer<T>>,
    max_size: usize,
    current_size: Arc<CacheLinePadded<AtomicUsize>>,
}

impl<T: QueueItem> EventQueue<T> {
    pub fn new(capacity: usize, max_size: usize) -> Self {
        let capacity = if capacity.is_power_of_two() {
            capacity
        } else {
            capacity.next_power_of_two()
        };

        EventQueue {
            buffer: Arc::new(LockFreeRingBuffer::new(capacity)),
            max_size,
            current_size: Arc::new(CacheLinePadded::new(AtomicUsize::new(0))),
        }
    }

    pub fn push(&self, item: T) -> Result<(), T> {
        let size = item.size();
        let current = self.current_size.value.load(Ordering::Relaxed);

        if current + size > self.max_size {
            return Err(item);
        }

        match self.buffer.push(item) {
            Ok(_) => {
                self.current_size.value.fetch_add(size, Ordering::Release);
                Ok(())
            }
            Err(item) => Err(item),
        }
    }

    pub fn pop(&self) -> Option<T> {
        if let Some(item) = self.buffer.pop() {
            let size = item.size();
            self.current_size.value.fetch_sub(size, Ordering::Release);
            Some(item)
        } else {
            None
        }
    }

    pub fn try_pop_batch(&self, max_items: usize) -> Vec<T> {
        let mut items = Vec::with_capacity(max_items);
        for _ in 0..max_items {
            if let Some(item) = self.pop() {
                items.push(item);
            } else {
                break;
            }
        }
        items
    }

    pub fn len(&self) -> usize {
        self.buffer.len()
    }

    pub fn is_empty(&self) -> bool {
        self.buffer.is_empty()
    }

    pub fn is_full(&self) -> bool {
        self.buffer.is_full()
    }

    pub fn current_size_bytes(&self) -> usize {
        self.current_size.value.load(Ordering::Acquire)
    }

    pub fn max_size_bytes(&self) -> usize {
        self.max_size
    }

    pub fn fill_percentage(&self) -> f64 {
        (self.current_size_bytes() as f64) / (self.max_size_bytes() as f64) * 100.0
    }

    pub fn buffer_fill_percentage(&self) -> f64 {
        (self.buffer.len() as f64) / (self.buffer.capacity() as f64) * 100.0
    }

    pub fn capacity(&self) -> usize {
        self.buffer.capacity()
    }
}

impl<T: QueueItem> Clone for EventQueue<T> {
    fn clone(&self) -> Self {
        EventQueue {
            buffer: Arc::clone(&self.buffer),
            max_size: self.max_size,
            current_size: Arc::clone(&self.current_size),
        }
    }
}

pub struct BatchBuffer<T: QueueItem> {
    items: Vec<T>,
    current_size: usize,
    max_size: usize,
}

impl<T: QueueItem> BatchBuffer<T> {
    pub fn new(max_items: usize, max_bytes: usize) -> Self {
        BatchBuffer {
            items: Vec::with_capacity(max_items),
            current_size: 0,
            max_size: max_bytes,
        }
    }

    pub fn push(&mut self, item: T) -> bool {
        let size = item.size();
        if self.current_size + size > self.max_size && !self.items.is_empty() {
            return false;
        }

        self.current_size += size;
        self.items.push(item);
        true
    }

    pub fn is_full(&self) -> bool {
        self.current_size >= self.max_size
    }

    pub fn is_empty(&self) -> bool {
        self.items.is_empty()
    }

    pub fn len(&self) -> usize {
        self.items.len()
    }

    pub fn current_size_bytes(&self) -> usize {
        self.current_size
    }

    pub fn drain(&mut self) -> Vec<T> {
        self.current_size = 0;
        std::mem::take(&mut self.items)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    struct MockItem {
        data: Vec<u8>,
    }

    impl QueueItem for MockItem {
        fn size(&self) -> usize {
            self.data.len() + 24
        }
    }

    #[test]
    fn test_lock_free_queue_push_pop() {
        let queue: EventQueue<MockItem> = EventQueue::new(1024, 10000);

        let item1 = MockItem { data: vec![0; 100] };
        assert!(queue.push(item1).is_ok());

        let item2 = MockItem { data: vec![0; 200] };
        assert!(queue.push(item2).is_ok());

        assert_eq!(queue.len(), 2);

        let popped = queue.pop();
        assert!(popped.is_some());
        assert_eq!(queue.len(), 1);
    }

    #[test]
    fn test_lock_free_queue_capacity() {
        let queue: EventQueue<MockItem> = EventQueue::new(64, 10000);
        assert_eq!(queue.capacity(), 64);
    }

    #[test]
    fn test_lock_free_queue_concurrent_push_pop() {
        use std::sync::Arc;
        use std::thread;

        let queue = Arc::new(EventQueue::new(1024, 100000));
        let mut handles = vec![];

        for i in 0..4 {
            let q = Arc::clone(&queue);
            let handle = thread::spawn(move || {
                for j in 0..100 {
                    let item = MockItem {
                        data: vec![0; 10 + (i * 10 + j) % 50],
                    };
                    let _ = q.push(item);
                }
            });
            handles.push(handle);
        }

        for handle in handles {
            handle.join().unwrap();
        }

        assert!(!queue.is_empty());
    }

    #[test]
    fn test_batch_buffer() {
        let mut buffer: BatchBuffer<MockItem> = BatchBuffer::new(10, 1000);

        let item = MockItem { data: vec![0; 100] };
        assert!(buffer.push(item));
        assert_eq!(buffer.len(), 1);

        let drained = buffer.drain();
        assert_eq!(drained.len(), 1);
        assert!(buffer.is_empty());
    }

    #[test]
    fn test_batch_buffer_full() {
        let mut buffer: BatchBuffer<MockItem> = BatchBuffer::new(10, 240);

        let item1 = MockItem { data: vec![0; 80] };
        assert!(buffer.push(item1));

        let item2 = MockItem { data: vec![0; 80] };
        assert!(buffer.push(item2));

        let item3 = MockItem { data: vec![0; 80] };
        assert!(!buffer.push(item3));
    }
}
