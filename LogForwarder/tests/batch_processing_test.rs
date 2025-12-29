use high_perf_forwarder::event::Event;
use tempfile::TempDir;
use std::fs;

/// Test to diagnose batch processing issue
/// When 400+ log messages are added, only 1 batch is being pushed instead of expected 4+ batches
#[tokio::test]
async fn test_batch_processing_diagnostic() {
    // Initialize logging
    let _ = tracing_subscriber::fmt()
        .with_max_level(tracing::Level::DEBUG)
        .with_test_writer()
        .try_init();

    let temp_dir = TempDir::new().unwrap();
    let log_file = temp_dir.path().join("test.log");
    
    // Create a log file with 400 lines
    let mut log_content = String::new();
    for i in 0..400 {
        log_content.push_str(&format!(
            "[2025-01-01 00:00:00.000] [INFO] [Thread-1] Log message #{}\n",
            i
        ));
    }
    fs::write(&log_file, log_content).expect("Failed to create test log file");
    
    // Verify file has expected content
    let lines: usize = fs::read_to_string(&log_file)
        .expect("Failed to read log file")
        .lines()
        .count();
    println!("Created test log file with {} lines", lines);
    assert_eq!(lines, 400, "Test file should have 400 lines");
    
    // Test batch accumulation directly
    println!("\n=== Testing Batch Accumulation ===");
    let batch_config = high_perf_forwarder::input_batch_manager::BatchManagerConfig::new(100, 5000);
    let mut accumulator = high_perf_forwarder::InputBatchAccumulator::new(batch_config);
    
    // Simulate reading batches of 100 events
    let mut batches_received = 0;
    for batch_num in 0..4 {
        // Create 100 events to simulate one read_events() call
        let mut events = Vec::new();
        for i in 0..100 {
            let event_id = batch_num * 100 + i;
            let event = Event::new(
                bytes::Bytes::from(format!("test_event_{}", event_id)),
                1000 + event_id as i64,
                event_id as u64,
                1,
                high_perf_forwarder::event::SourceType::Json,
                1,
                1,
            );
            events.push(event);
        }
        
        println!("Batch {}: Adding {} events to accumulator", batch_num + 1, events.len());
        match accumulator.add_events(events) {
            Some(batch) => {
                println!("  → Batch ready! Got {} events", batch.len());
                batches_received += 1;
                assert_eq!(batch.len(), 100, "Each batch should have exactly 100 events");
            }
            None => {
                println!("  → No batch ready yet. Accumulator has {} / {} events",
                    accumulator.current_batch_size(),
                    accumulator.get_batch_size()
                );
            }
        }
    }
    
    println!("\nBatches received: {}", batches_received);
    assert_eq!(batches_received, 4, "Should receive 4 batches of 100 events each");
    
    // Test FileInput::read_events behavior
    println!("\n=== Testing FileInput::read_events ===");
    let config = high_perf_forwarder::config::FileInputConfig {
        name: "test".to_string(),
        path: log_file.to_string_lossy().to_string(),
        sourcetype: "application".to_string(),
        index: "main".to_string(),
        recursive: false,
        follow_tail: false,
        encoding: "utf-8".to_string(),
        batch_size: 100,
        read_buffer_kb: 128,
        checkpoint_interval_ms: 5000,
        exclude: None,
    };
    
    let checkpoint_dir = temp_dir.path().join("checkpoint");
    fs::create_dir_all(&checkpoint_dir).expect("Failed to create checkpoint dir");
    
    if let Ok(mut file_input) = high_perf_forwarder::inputs::FileInput::open(
        config,
        &log_file,
    ) {
        let mut total_events = 0;
        let mut read_count = 0;
        
        loop {
            match file_input.read_events() {
                Ok(events) => {
                    let count = events.len();
                    total_events += count;
                    read_count += 1;
                    
                    println!("Read #{}: Got {} events (total: {})", read_count, count, total_events);
                    
                    if count == 0 {
                        // EOF
                        println!("Reached EOF");
                        break;
                    }
                }
                Err(e) => {
                    println!("Error reading events: {}", e);
                    break;
                }
            }
        }
        
        println!("\nTotal reads: {}", read_count);
        println!("Total events: {}", total_events);
        assert_eq!(total_events, 400, "Should read all 400 events");
    } else {
        panic!("Failed to open test log file");
    }
}
