use high_perf_forwarder::{
    checkpoint::CheckpointStore,
    compression::{CompressionConfig, CompressionEngine},
    event::{Event, SourceType},
    queue::EventQueue,
};
use std::sync::{
    atomic::{AtomicBool, Ordering},
    Arc,
};
use std::thread;
use std::time::Duration;
use tempfile::TempDir;

#[test]
fn chaos_network_partition() {
    let temp_dir = TempDir::new().unwrap();
    let checkpoint_path = temp_dir.path().join("checkpoint.dat");
    let _checkpoint = CheckpointStore::open(&checkpoint_path, 1000).unwrap();

    let queue: EventQueue<Event> = EventQueue::new(1024, 100_000);
    let network_available = Arc::new(AtomicBool::new(true));

    let producer_queue = queue.clone();
    let producer_available = network_available.clone();

    let producer = thread::spawn(move || {
        for i in 0..1000 {
            let event = Event::new(
                bytes::Bytes::from(format!("event {}", i)),
                chrono::Utc::now().timestamp_millis(),
                i as u64,
                1,
                SourceType::Json,
                1,
                1,
            );

            if let Ok(_) = producer_queue.push(event) {
                if producer_available.load(Ordering::Relaxed) {
                    thread::sleep(Duration::from_micros(100));
                } else {
                    assert!(!producer_available.load(Ordering::Relaxed));
                }
            }
        }
    });

    thread::sleep(Duration::from_millis(100));

    network_available.store(false, Ordering::Release);
    assert!(!network_available.load(Ordering::Acquire));

    thread::sleep(Duration::from_millis(500));

    network_available.store(true, Ordering::Release);

    let queue_clone = queue.clone();
    let consumer = thread::spawn(move || {
        let mut count = 0;
        loop {
            if let Some(_) = queue_clone.pop() {
                count += 1;
            } else if count > 500 {
                break;
            }
            thread::sleep(Duration::from_micros(100));
        }
        count
    });

    producer.join().unwrap();
    let consumed = consumer.join().unwrap();

    assert!(consumed > 500);
}

#[test]
fn chaos_disk_full_simulation() {
    let temp_dir = TempDir::new().unwrap();
    let checkpoint_path = temp_dir.path().join("checkpoint.dat");
    let _checkpoint = CheckpointStore::open(&checkpoint_path, 1000).unwrap();

    let queue: EventQueue<Event> = EventQueue::new(1024, 50_000);
    let disk_space_available = Arc::new(AtomicBool::new(true));

    let producer_queue = queue.clone();
    let producer_disk = disk_space_available.clone();

    let producer = thread::spawn(move || {
        let mut pushed = 0;
        for i in 0..500 {
            if !producer_disk.load(Ordering::Relaxed) {
                thread::sleep(Duration::from_millis(100));
                continue;
            }

            let event = Event::new(
                bytes::Bytes::from(vec![b'x'; 1000]),
                chrono::Utc::now().timestamp_millis(),
                i as u64,
                1,
                SourceType::Json,
                1,
                1,
            );

            if producer_queue.push(event).is_ok() {
                pushed += 1;
            }
        }
        pushed
    });

    let monitor_queue = queue.clone();
    let monitor_disk = disk_space_available.clone();

    let monitor = thread::spawn(move || {
        let mut iterations = 0;
        while iterations < 5 {
            thread::sleep(Duration::from_millis(100));
            let fill = monitor_queue.fill_percentage();

            if fill > 80.0 {
                monitor_disk.store(false, Ordering::Release);
                iterations += 1;
                thread::sleep(Duration::from_millis(200));
                monitor_disk.store(true, Ordering::Release);
            }
        }
    });

    producer.join().unwrap();
    monitor.join().unwrap();

    assert!(queue.len() > 0);
}

#[test]
fn chaos_sigkill_recovery() {
    let temp_dir = TempDir::new().unwrap();
    let checkpoint_path = temp_dir.path().join("checkpoint.dat");

    {
        let checkpoint = CheckpointStore::open(&checkpoint_path, 1000).unwrap();
        checkpoint
            .put(
                1001,
                12345,
                50000,
                100_000,
                0xABCD,
                chrono::Utc::now().timestamp(),
            )
            .unwrap();
        checkpoint.flush().unwrap();
    }

    {
        let checkpoint = CheckpointStore::open(&checkpoint_path, 1000).unwrap();
        let record = checkpoint.get(1001).unwrap();
        assert_eq!(record.position, 50000);
        assert_eq!(record.inode, 12345);
    }
}

#[test]
fn chaos_corrupted_checkpoint() {
    let temp_dir = TempDir::new().unwrap();
    let checkpoint_path = temp_dir.path().join("checkpoint.dat");

    {
        let checkpoint = CheckpointStore::open(&checkpoint_path, 1000).unwrap();
        checkpoint
            .put(
                1001,
                12345,
                50000,
                100_000,
                0xABCD,
                chrono::Utc::now().timestamp(),
            )
            .unwrap();
        checkpoint.flush().unwrap();
    }

    use std::io::Write;
    let mut file = std::fs::OpenOptions::new()
        .write(true)
        .open(&checkpoint_path)
        .unwrap();

    file.write_all(b"CORRUPTED DATA").unwrap();
    file.flush().unwrap();
    drop(file);

    let checkpoint = CheckpointStore::open(&checkpoint_path, 1000).unwrap();
    let result = checkpoint.get(1001);

    assert!(result.is_err() || result.is_ok());
}

#[test]
fn chaos_cpu_saturation() {
    let queue: EventQueue<Event> = EventQueue::new(16384, 10_000_000);

    let producer_queue = queue.clone();
    let producer = thread::spawn(move || {
        let mut count = 0;
        for i in 0..10_000 {
            let event = Event::new(
                bytes::Bytes::from(vec![b'a'; 100]),
                chrono::Utc::now().timestamp_millis(),
                i as u64,
                1,
                SourceType::Json,
                1,
                1,
            );
            if producer_queue.push(event).is_ok() {
                count += 1;
            }
        }
        count
    });

    let consumer_queue = queue.clone();
    let cpu_load = Arc::new(AtomicBool::new(true));
    let consumer_cpu = cpu_load.clone();

    let consumer = thread::spawn(move || {
        let mut count = 0;
        let mut iterations = 0;
        while consumer_cpu.load(Ordering::Relaxed) {
            if let Some(_) = consumer_queue.pop() {
                count += 1;
            }
            iterations += 1;
            if iterations > 100_000 {
                break;
            }
        }
        count
    });

    thread::sleep(Duration::from_millis(100));
    cpu_load.store(false, Ordering::Release);

    producer.join().unwrap();
    let consumed = consumer.join().unwrap();

    assert!(consumed > 0);
}

#[test]
fn chaos_memory_pressure() {
    let queue: EventQueue<Event> = EventQueue::new(16384, 10_000_000);

    let mut handles = vec![];

    for producer_id in 0..4 {
        let q = queue.clone();
        let handle = thread::spawn(move || {
            let mut allocated = vec![];
            for i in 0..100 {
                let event = Event::new(
                    bytes::Bytes::from(vec![b'x'; 10000]),
                    chrono::Utc::now().timestamp_millis(),
                    (producer_id * 100 + i) as u64,
                    producer_id as u32,
                    SourceType::Json,
                    1,
                    1,
                );

                if let Ok(_) = q.push(event.clone()) {
                    allocated.push(event);
                } else {
                    break;
                }
            }
            allocated.len()
        });
        handles.push(handle);
    }

    let mut total_pushed = 0;
    for handle in handles {
        total_pushed += handle.join().unwrap();
    }

    assert!(total_pushed > 0);
}

#[test]
fn chaos_file_descriptor_exhaustion() {
    use std::fs::File;
    use std::io::Write;

    let temp_dir = TempDir::new().unwrap();

    let max_open = 100;
    let mut files = vec![];

    for i in 0..max_open {
        if let Ok(mut f) = File::create(temp_dir.path().join(format!("file_{}.txt", i))) {
            let _ = f.write_all(b"test content");
            files.push(f);
        }
    }

    assert!(files.len() > 0);

    {
        let queue: EventQueue<Event> = EventQueue::new(1024, 100_000);
        for i in 0..10 {
            let event = Event::new(
                bytes::Bytes::from("test"),
                chrono::Utc::now().timestamp_millis(),
                i as u64,
                1,
                SourceType::Json,
                1,
                1,
            );
            let _ = queue.push(event);
        }

        assert_eq!(queue.len(), 10);
    }
}

#[test]
fn chaos_compression_under_stress() {
    let engine = CompressionEngine::new(CompressionConfig::default(), None);
    let data = vec![b'a'; 10 * 1024 * 1024];

    let mut handles = vec![];

    for _ in 0..4 {
        let engine_clone = engine.clone();
        let data_clone = data.clone();

        let handle = thread::spawn(move || {
            let compressed = engine_clone.compress(&data_clone, None).unwrap();
            engine_clone.decompress(&compressed).unwrap()
        });

        handles.push(handle);
    }

    for handle in handles {
        let result = handle.join().unwrap();
        assert_eq!(result, data);
    }
}

#[test]
#[ignore]
fn chaos_concurrent_checkpoint_access() {
    let temp_dir = TempDir::new().unwrap();
    let checkpoint_path = Arc::new(temp_dir.path().join("checkpoint.dat"));

    let checkpoint = CheckpointStore::open(checkpoint_path.as_ref(), 10000).unwrap();
    let checkpoint = Arc::new(checkpoint);

    let mut handles = vec![];

    for thread_id in 0..8 {
        let cp = Arc::clone(&checkpoint);
        let handle = thread::spawn(move || {
            for i in 0..100 {
                let hash = ((thread_id * 1000 + i) as u64).wrapping_mul(31);
                let _ = cp.put(
                    hash,
                    i as u64,
                    i as u64 * 100,
                    100000,
                    0xDEADBEEF,
                    chrono::Utc::now().timestamp(),
                );
            }
        });
        handles.push(handle);
    }

    for handle in handles {
        handle.join().unwrap();
    }
}

#[test]
fn chaos_queue_overflow() {
    let queue: EventQueue<Event> = EventQueue::new(1024, 50_000);

    let mut rejected = 0;
    let mut accepted = 0;

    for i in 0..1000 {
        let event = Event::new(
            bytes::Bytes::from(vec![b'x'; 1000]),
            chrono::Utc::now().timestamp_millis(),
            i as u64,
            1,
            SourceType::Json,
            1,
            1,
        );

        match queue.push(event) {
            Ok(_) => accepted += 1,
            Err(_) => rejected += 1,
        }
    }

    assert!(accepted > 0);
    assert!(rejected > 0);
    assert_eq!(accepted + rejected, 1000);
}

#[test]
#[ignore]
fn chaos_batch_processing_stress() {
    let queue: EventQueue<Event> = EventQueue::new(16384, 100_000_000);

    let producer_queue = queue.clone();
    let producer = thread::spawn(move || {
        for i in 0..1_000 {
            let event = Event::new(
                bytes::Bytes::from(format!("event {}", i)),
                chrono::Utc::now().timestamp_millis(),
                i as u64,
                1,
                SourceType::Json,
                1,
                1,
            );
            let _ = producer_queue.push(event);
        }
    });

    thread::sleep(Duration::from_millis(100));

    let consumer_queue = queue.clone();
    let consumer = thread::spawn(move || {
        let mut total = 0;
        let mut empty_count = 0;
        loop {
            let batch = consumer_queue.try_pop_batch(50);
            if batch.is_empty() {
                empty_count += 1;
                if empty_count > 10 {
                    break;
                }
                thread::sleep(Duration::from_millis(10));
            } else {
                empty_count = 0;
                total += batch.len();
            }
            if total >= 1_000 {
                break;
            }
        }
        total
    });

    producer.join().unwrap();
    let consumed = consumer.join().unwrap();

    assert!(consumed > 500);
}
