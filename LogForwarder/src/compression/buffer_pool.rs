use parking_lot::Mutex;
use std::io::Write;
use std::sync::Arc;

pub const BUFFER_SIZE: usize = 2 * 1024 * 1024;
pub const POOL_SIZE: usize = 64;

pub struct BufferPool {
    buffers: Arc<Mutex<Vec<Vec<u8>>>>,
}

impl BufferPool {
    pub fn new() -> Self {
        let mut buffers = Vec::with_capacity(POOL_SIZE);
        for _ in 0..POOL_SIZE {
            buffers.push(Vec::with_capacity(BUFFER_SIZE));
        }

        BufferPool {
            buffers: Arc::new(Mutex::new(buffers)),
        }
    }

    pub fn acquire(&self) -> PooledBuffer {
        let mut pool = self.buffers.lock();
        let buffer = if let Some(mut buf) = pool.pop() {
            buf.clear();
            buf
        } else {
            Vec::with_capacity(BUFFER_SIZE)
        };

        PooledBuffer {
            buffer,
            pool: Arc::clone(&self.buffers),
        }
    }

    pub fn size(&self) -> usize {
        self.buffers.lock().len()
    }
}

impl Clone for BufferPool {
    fn clone(&self) -> Self {
        BufferPool {
            buffers: Arc::clone(&self.buffers),
        }
    }
}

pub struct PooledBuffer {
    buffer: Vec<u8>,
    pool: Arc<Mutex<Vec<Vec<u8>>>>,
}

impl PooledBuffer {
    pub fn as_slice(&self) -> &[u8] {
        &self.buffer
    }

    pub fn as_mut_slice(&mut self) -> &mut [u8] {
        &mut self.buffer
    }

    pub fn len(&self) -> usize {
        self.buffer.len()
    }

    pub fn is_empty(&self) -> bool {
        self.buffer.is_empty()
    }

    pub fn set_len(&mut self, len: usize) {
        if len <= self.buffer.capacity() {
            unsafe {
                self.buffer.set_len(len);
            }
        }
    }

    pub fn reserve(&mut self, additional: usize) {
        self.buffer.reserve(additional);
    }

    pub fn clear(&mut self) {
        self.buffer.clear();
    }

    pub fn extend_from_slice(&mut self, slice: &[u8]) {
        self.buffer.extend_from_slice(slice);
    }

    pub fn into_inner(mut self) -> Vec<u8> {
        std::mem::take(&mut self.buffer)
    }
}

impl Drop for PooledBuffer {
    fn drop(&mut self) {
        let mut pool = self.pool.lock();
        if pool.len() < POOL_SIZE {
            let mut buf = std::mem::take(&mut self.buffer);
            buf.clear();
            pool.push(buf);
        }
    }
}

impl Write for PooledBuffer {
    fn write(&mut self, buf: &[u8]) -> std::io::Result<usize> {
        self.buffer.extend_from_slice(buf);
        Ok(buf.len())
    }

    fn flush(&mut self) -> std::io::Result<()> {
        Ok(())
    }
}

impl Default for BufferPool {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_buffer_pool_acquire_and_release() {
        let pool = BufferPool::new();
        assert_eq!(pool.size(), POOL_SIZE);

        let buf1 = pool.acquire();
        assert_eq!(pool.size(), POOL_SIZE - 1);

        drop(buf1);
        assert_eq!(pool.size(), POOL_SIZE);
    }

    #[test]
    fn test_buffer_reuse() {
        let pool = BufferPool::new();
        let mut buf1 = pool.acquire();
        buf1.extend_from_slice(b"test data");
        let ptr1 = buf1.as_slice().as_ptr();

        drop(buf1);

        let mut buf2 = pool.acquire();
        assert!(buf2.is_empty());
        buf2.extend_from_slice(b"new data");
        let ptr2 = buf2.as_slice().as_ptr();

        assert_eq!(ptr1, ptr2);
    }

    #[test]
    fn test_pool_capacity_limit() {
        let pool = BufferPool::new();

        let mut buffers = Vec::new();
        for _ in 0..POOL_SIZE {
            buffers.push(pool.acquire());
        }

        assert_eq!(pool.size(), 0);

        drop(buffers);
        assert_eq!(pool.size(), POOL_SIZE);
    }
}
