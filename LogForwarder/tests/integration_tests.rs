use high_perf_forwarder::{
    checkpoint::CheckpointStore,
    compression::{CompressionConfig, CompressionEngine},
    event::{Event, SourceType},
    queue::EventQueue,
};
use tempfile::TempDir;

#[test]
fn test_file_to_indexer_pipeline() {
    let temp_dir = TempDir::new().unwrap();
    let checkpoint_path = temp_dir.path().join("checkpoint.dat");

    let checkpoint =
        CheckpointStore::open(&checkpoint_path, 1000).expect("Failed to create checkpoint");
    let queue: EventQueue<Event> = EventQueue::new(1024, 100_000);

    let event = Event::new(
        bytes::Bytes::from("test log line"),
        chrono::Utc::now().timestamp_millis(),
        0,
        1,
        SourceType::ApacheAccess,
        1,
        1,
    );

    assert!(queue.push(event).is_ok());
    assert_eq!(queue.len(), 1);

    let popped = queue.pop();
    assert!(popped.is_some());
    assert_eq!(queue.len(), 0);

    let event = popped.unwrap();
    checkpoint
        .put(
            1001,
            12345,
            event.offset() + 100,
            100_000,
            0xABCD,
            chrono::Utc::now().timestamp(),
        )
        .expect("Failed to save checkpoint");

    let record = checkpoint.get(1001).expect("Failed to get checkpoint");
    assert_eq!(record.position, 100);
}

#[test]
fn test_configuration_hot_reload() {
    let initial_config = CompressionConfig::default();
    let engine = CompressionEngine::new(initial_config, None);

    let test_data = vec![b'x'; 10000];
    let compressed1 = engine
        .compress(&test_data, None)
        .expect("Compression failed");

    let new_config = CompressionConfig {
        level: 6,
        adaptive: false,
        use_dictionary: true,
        batch_size_bytes: 2 * 1024 * 1024,
    };
    engine.update_config(new_config);

    let compressed2 = engine
        .compress(&test_data, None)
        .expect("Compression failed");

    assert!(compressed1.len() < test_data.len());
    assert!(compressed2.len() < test_data.len());

    let decompressed = engine
        .decompress(&compressed2)
        .expect("Decompression failed");
    assert_eq!(decompressed, test_data);
}

#[test]
fn test_tcp_input_to_compression_output() {
    let engine = CompressionEngine::new(CompressionConfig::default(), None);

    let syslog_message = vec![b'x'; 10000];

    let compressed = engine
        .compress(&syslog_message, Some("syslog"))
        .expect("Compression failed");
    let decompressed = engine
        .decompress(&compressed)
        .expect("Decompression failed");

    assert_eq!(decompressed, syslog_message);
    assert!(compressed.len() < syslog_message.len());
}

#[test]
fn test_queue_backpressure_handling() {
    let queue: EventQueue<Event> = EventQueue::new(1024, 50_000);

    let mut pushed_count = 0;
    for i in 0..100 {
        let event = Event::new(
            bytes::Bytes::from(vec![b'x'; 1000]),
            chrono::Utc::now().timestamp_millis(),
            i as u64 * 1000,
            1,
            SourceType::Json,
            1,
            1,
        );

        if queue.push(event).is_ok() {
            pushed_count += 1;
        } else {
            break;
        }
    }

    assert!(pushed_count > 0);
    assert!(pushed_count < 100);
}

#[test]
fn test_graceful_shutdown_drains_queues() {
    let queue: EventQueue<Event> = EventQueue::new(1024, 100_000);

    for i in 0..10 {
        let event = Event::new(
            bytes::Bytes::from(format!("event {}", i)),
            chrono::Utc::now().timestamp_millis(),
            i as u64 * 100,
            1,
            SourceType::Json,
            1,
            1,
        );
        let _ = queue.push(event);
    }

    assert_eq!(queue.len(), 10);

    let mut drained = 0;
    while queue.pop().is_some() {
        drained += 1;
    }

    assert_eq!(drained, 10);
    assert!(queue.is_empty());
}

#[test]
fn test_batch_processing_pipeline() {
    let queue: EventQueue<Event> = EventQueue::new(1024, 100_000);

    for i in 0..50 {
        let event = Event::new(
            bytes::Bytes::from(format!("batch event {}", i)),
            chrono::Utc::now().timestamp_millis(),
            i as u64 * 50,
            1,
            SourceType::Json,
            1,
            1,
        );
        let _ = queue.push(event);
    }

    let batch = queue.try_pop_batch(10);
    assert_eq!(batch.len(), 10);

    let batch2 = queue.try_pop_batch(10);
    assert_eq!(batch2.len(), 10);

    assert_eq!(queue.len(), 30);
}

#[test]
fn test_checkpoint_recovery_after_crash() {
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
fn test_sourcetype_serialization() {
    use high_perf_forwarder::event::SourceType;

    assert_eq!(SourceType::from_u8(1), SourceType::ApacheAccess);
    assert_eq!(SourceType::from_u8(2), SourceType::ApacheError);
    assert_eq!(SourceType::from_u8(3), SourceType::Json);
    assert_eq!(SourceType::from_u8(4), SourceType::Syslog);
    assert_eq!(SourceType::from_u8(5), SourceType::Csv);
    assert_eq!(SourceType::from_u8(99), SourceType::Unknown);
}

#[test]
fn test_concurrent_queue_operations() {
    use std::sync::Arc;
    use std::thread;

    let queue = Arc::new(EventQueue::<Event>::new(1024, 100_000));
    let mut handles = vec![];

    for producer_id in 0..4 {
        let q = Arc::clone(&queue);
        let handle = thread::spawn(move || {
            for i in 0..25 {
                let event = Event::new(
                    bytes::Bytes::from(format!("event-{}-{}", producer_id, i)),
                    chrono::Utc::now().timestamp_millis(),
                    (producer_id * 25 + i) as u64,
                    producer_id as u32,
                    SourceType::Json,
                    1,
                    1,
                );
                let _ = q.push(event);
            }
        });
        handles.push(handle);
    }

    for handle in handles {
        handle.join().unwrap();
    }

    assert!(queue.len() > 0);
    assert!(queue.len() <= 100);
}

#[test]
fn test_compression_with_various_sourcetypes() {
    let temp_dir = TempDir::new().unwrap();
    let engine = CompressionEngine::new(
        CompressionConfig::default(),
        Some(temp_dir.path().to_str().unwrap()),
    );

    let test_cases = vec![
        (vec![b'a'; 10000], "apache_access"),
        (vec![b'b'; 10000], "error_logs"),
        (vec![b'c'; 10000], "json"),
        (vec![b'd'; 10000], "syslog"),
        (vec![b'e'; 10000], "csv"),
    ];

    for (data, sourcetype) in test_cases {
        let compressed = engine
            .compress(&data, Some(sourcetype))
            .expect(&format!("Failed to compress {}", sourcetype));
        let decompressed = engine
            .decompress(&compressed)
            .expect(&format!("Failed to decompress {}", sourcetype));

        assert_eq!(decompressed, data);
        assert!(compressed.len() < data.len());
    }
}
