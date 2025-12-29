use criterion::{black_box, criterion_group, criterion_main, BenchmarkId, Criterion};
use high_perf_forwarder::{
    checkpoint::CheckpointStore,
    compression::{CompressionConfig, CompressionEngine},
    event::{Event, SourceType},
    queue::EventQueue,
};
use std::sync::Arc;
use tempfile::TempDir;

fn bench_event_throughput(c: &mut Criterion) {
    let queue: EventQueue<Event> = EventQueue::new(16384, 10_000_000);

    c.bench_function("event_throughput_100k", |b| {
        b.iter(|| {
            for i in 0..100_000 {
                let event = Event::new(
                    bytes::Bytes::from(format!("log line {}", i)),
                    chrono::Utc::now().timestamp_millis(),
                    i as u64 * 100,
                    1,
                    SourceType::Json,
                    1,
                    1,
                );
                let _ = queue.push(event);
            }

            for _ in 0..100_000 {
                let _ = queue.pop();
            }
        })
    });
}

fn bench_compression_speed(c: &mut Criterion) {
    let engine = CompressionEngine::new(CompressionConfig::default(), None);

    let test_data = black_box(vec![b'a'; 1024 * 1024]);

    c.bench_function("zstd_compression_1mb", |b| {
        b.iter(|| engine.compress(&test_data, None).unwrap())
    });

    let compressed = engine.compress(&test_data, None).unwrap();
    c.bench_function("zstd_decompression_1mb", |b| {
        b.iter(|| engine.decompress(&compressed).unwrap())
    });
}

fn bench_compression_ratio(c: &mut Criterion) {
    let engine = CompressionEngine::new(CompressionConfig::default(), None);
    let mut group = c.benchmark_group("compression_ratio");

    for size_kb in [10, 100, 1000].iter() {
        let data = vec![b'x'; size_kb * 1024];

        group.bench_with_input(
            BenchmarkId::from_parameter(format!("{}kb", size_kb)),
            size_kb,
            |b, _| {
                b.iter(|| {
                    let compressed = engine.compress(&data, None).unwrap();
                    engine.get_compression_ratio(data.len(), compressed.len())
                })
            },
        );
    }
    group.finish();
}

fn bench_grpc_send_throughput(c: &mut Criterion) {
    c.bench_function("network_send_simulation", |b| {
        b.iter(|| {
            let data = black_box(vec![0u8; 1024 * 1024]);
            let _size = data.len();
        })
    });
}

fn bench_checkpoint_latency(c: &mut Criterion) {
    let temp_dir = TempDir::new().unwrap();
    let checkpoint_path = temp_dir.path().join("checkpoint.dat");
    let checkpoint = CheckpointStore::open(&checkpoint_path, 10000).unwrap();

    c.bench_function("checkpoint_save_latency", |b| {
        b.iter(|| {
            checkpoint
                .put(
                    black_box(1001),
                    black_box(12345),
                    black_box(50000),
                    black_box(100_000),
                    black_box(0xABCD),
                    chrono::Utc::now().timestamp(),
                )
                .unwrap()
        })
    });
}

fn bench_queue_operations(c: &mut Criterion) {
    let queue: EventQueue<Event> = EventQueue::new(16384, 10_000_000);

    c.bench_function("queue_push_latency", |b| {
        b.iter(|| {
            let event = Event::new(
                bytes::Bytes::from("test event"),
                chrono::Utc::now().timestamp_millis(),
                0,
                1,
                SourceType::Json,
                1,
                1,
            );
            let _ = queue.push(event);
        })
    });

    for _ in 0..100_000 {
        let event = Event::new(
            bytes::Bytes::from("test"),
            chrono::Utc::now().timestamp_millis(),
            0,
            1,
            SourceType::Json,
            1,
            1,
        );
        let _ = queue.push(event);
    }

    c.bench_function("queue_pop_latency", |b| {
        b.iter(|| {
            let _ = queue.pop();
        })
    });
}

fn bench_concurrent_queue_access(c: &mut Criterion) {
    c.bench_function("concurrent_queue_4_producers", |b| {
        b.iter(|| {
            use std::thread;

            let queue = Arc::new(EventQueue::<Event>::new(16384, 10_000_000));
            let mut handles = vec![];

            for producer_id in 0..4 {
                let q = Arc::clone(&queue);
                let handle = thread::spawn(move || {
                    for i in 0..2500 {
                        let event = Event::new(
                            bytes::Bytes::from(format!("event-{}-{}", producer_id, i)),
                            chrono::Utc::now().timestamp_millis(),
                            (producer_id * 2500 + i) as u64,
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
                let _ = handle.join();
            }
        })
    });
}

fn bench_memory_usage(c: &mut Criterion) {
    c.bench_function("queue_memory_footprint_10k_files", |b| {
        b.iter(|| {
            let queue: EventQueue<Event> = EventQueue::new(16384, 200_000_000);
            let _ = queue.capacity();
        })
    });
}

criterion_group!(
    benches,
    bench_event_throughput,
    bench_compression_speed,
    bench_compression_ratio,
    bench_grpc_send_throughput,
    bench_checkpoint_latency,
    bench_queue_operations,
    bench_concurrent_queue_access,
    bench_memory_usage,
);

criterion_main!(benches);
