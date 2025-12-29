use high_perf_forwarder::parser::{
    FormatDetector, JsonParser, SyslogParser, SyslogFormat, LogPipeline, RawParser, LogParser,
};
use high_perf_forwarder::enrichment::EnrichmentPipeline;
use high_perf_forwarder::masking::MaskingEngine;
use high_perf_forwarder::routing::ConditionalRouter;
use std::sync::Arc;

#[test]
fn test_format_detection_json_confidence() {
    let json_parser = Arc::new(JsonParser::new("json".to_string(), None));
    let detector = FormatDetector::new(vec![json_parser], 100, 0.8, 10000);

    let json_line = r#"{"level":"INFO","message":"test message","timestamp":"2025-01-01T00:00:00Z"}"#;
    let result = detector.detect(json_line);

    assert!(result.is_some());
    assert_eq!(result.unwrap(), 0);
}

#[test]
fn test_format_detection_syslog_confidence() {
    let syslog_parser = Arc::new(
        SyslogParser::new("syslog".to_string(), SyslogFormat::RFC5424).unwrap(),
    );
    let detector = FormatDetector::new(vec![syslog_parser], 100, 0.8, 10000);

    let syslog_line = r#"<134>1 2025-01-01T10:30:45Z myhost myapp 1234 ID47 - test message"#;
    let result = detector.detect(syslog_line);

    assert!(result.is_some());
    assert_eq!(result.unwrap(), 0);
}

#[test]
fn test_format_detection_priority_order() {
    let json_parser = Arc::new(JsonParser::new("json".to_string(), None));
    let syslog_parser = Arc::new(
        SyslogParser::new("syslog".to_string(), SyslogFormat::RFC5424).unwrap(),
    );

    let detector = FormatDetector::new(
        vec![json_parser, syslog_parser],
        100,
        0.8,
        10000,
    );

    let json_line = r#"{"level":"INFO","message":"test"}"#;

    let first_detection = detector.detect(json_line);
    assert_eq!(first_detection.unwrap(), 0);
}

#[test]
fn test_format_detection_cache_effectiveness() {
    let json_parser = Arc::new(JsonParser::new("json".to_string(), None));
    let detector = FormatDetector::new(vec![json_parser], 100, 0.8, 10000);

    let json_line = r#"{"level":"INFO","message":"test"}"#;

    for _ in 0..100 {
        let result = detector.detect(json_line);
        assert!(result.is_some());
    }

    let stats = detector.stats();
    assert_eq!(stats.total_lines_processed, 100);
}

#[test]
fn test_format_detection_re_evaluation() {
    let json_parser = Arc::new(JsonParser::new("json".to_string(), None));
    let detector = FormatDetector::new(vec![json_parser], 10, 0.8, 10);

    let json_line = r#"{"level":"INFO","message":"test"}"#;

    for _ in 0..20 {
        detector.detect(json_line);
    }

    let stats = detector.stats();
    assert_eq!(stats.total_lines_processed, 20);
}

#[test]
fn test_format_detection_unknown_format() {
    let json_parser = Arc::new(JsonParser::new("json".to_string(), None));
    let detector = FormatDetector::new(vec![json_parser], 100, 0.8, 10000);

    let unknown_line = "completely random text that doesn't match any format";
    let result = detector.detect(unknown_line);

    assert!(result.is_none());
}

#[test]
fn test_format_detection_multiple_parsers_best_match() {
    let json_parser = Arc::new(JsonParser::new("json".to_string(), None));
    let syslog_parser = Arc::new(
        SyslogParser::new("syslog".to_string(), SyslogFormat::RFC5424).unwrap(),
    );
    let raw_parser = Arc::new(RawParser::new("raw".to_string()));

    let detector = FormatDetector::new(
        vec![json_parser, syslog_parser, raw_parser],
        100,
        0.5,
        10000,
    );

    let json_line = r#"{"level":"INFO","message":"test"}"#;
    let result = detector.detect(json_line);

    assert_eq!(result.unwrap(), 0);
}

#[tokio::test]
async fn test_log_pipeline_json_parsing() {
    let json_parser = Arc::new(JsonParser::new("json".to_string(), None));
    let detector = Arc::new(FormatDetector::new(vec![json_parser], 100, 0.8, 10000));
    let enricher = Arc::new(EnrichmentPipeline::new());
    let masker = Arc::new(MaskingEngine::new());
    let router = Arc::new(ConditionalRouter::new("default".to_string()));

    let pipeline = LogPipeline::new(detector, enricher, masker, router);

    let json_line = r#"{"level":"INFO","message":"test message","timestamp":"2025-01-01T00:00:00Z"}"#;
    let result = pipeline.process(json_line).await;

    assert!(result.is_ok());
    let event = result.unwrap();
    assert_eq!(event.parsed.parser_id, "json");
    assert_eq!(
        event.fields.get("level").and_then(|v| v.as_str()),
        Some("INFO")
    );
}

#[tokio::test]
async fn test_log_pipeline_batch_processing() {
    let json_parser = Arc::new(JsonParser::new("json".to_string(), None));
    let detector = Arc::new(FormatDetector::new(vec![json_parser], 100, 0.8, 10000));
    let enricher = Arc::new(EnrichmentPipeline::new());
    let masker = Arc::new(MaskingEngine::new());
    let router = Arc::new(ConditionalRouter::new("default".to_string()));

    let pipeline = LogPipeline::new(detector, enricher, masker, router);

    let lines = vec![
        r#"{"level":"INFO","message":"msg1","timestamp":"2025-01-01T00:00:00Z"}"#.to_string(),
        r#"{"level":"ERROR","message":"msg2","timestamp":"2025-01-01T00:00:01Z"}"#.to_string(),
        r#"{"level":"WARN","message":"msg3","timestamp":"2025-01-01T00:00:02Z"}"#.to_string(),
    ];

    let results = pipeline.process_batch(lines).await;
    assert_eq!(results.len(), 3);
    assert!(results.iter().all(|r| r.is_ok()));
}

#[test]
fn test_format_detection_json_vs_syslog() {
    let json_parser = Arc::new(JsonParser::new("json".to_string(), None));
    let syslog_parser = Arc::new(
        SyslogParser::new("syslog".to_string(), SyslogFormat::RFC5424).unwrap(),
    );

    let detector = FormatDetector::new(
        vec![json_parser, syslog_parser],
        100,
        0.7,
        10000,
    );

    let json_line = r#"{"level":"INFO","message":"test"}"#;
    assert_eq!(detector.detect(json_line).unwrap(), 0);

    let syslog_line = r#"<134>1 2025-01-01T10:30:45Z myhost myapp 1234 ID47 - test"#;
    assert_eq!(detector.detect(syslog_line).unwrap(), 1);
}

#[test]
fn test_format_detector_low_confidence_threshold() {
    let json_parser = Arc::new(JsonParser::new("json".to_string(), None));
    let detector = FormatDetector::new(vec![json_parser], 100, 0.95, 10000);

    let almost_json = "{ not really json }";
    let result = detector.detect(almost_json);

    assert!(result.is_none());
}

#[test]
fn test_format_detector_get_parser() {
    let json_parser = Arc::new(JsonParser::new("json".to_string(), None));
    let detector = FormatDetector::new(vec![json_parser.clone()], 100, 0.8, 10000);

    let retrieved = detector.get_parser(0);
    assert!(retrieved.is_some());
    assert_eq!(retrieved.unwrap().name(), "json");
}

#[test]
fn test_raw_parser_fallback() {
    let raw_parser: Arc<dyn LogParser> = Arc::new(RawParser::new("raw".to_string()));
    assert_eq!(raw_parser.can_parse("anything"), 0.1);
}

#[tokio::test]
async fn test_pipeline_with_raw_parser_fallback() {
    let json_parser = Arc::new(JsonParser::new("json".to_string(), None));
    let raw_parser = Arc::new(RawParser::new("raw".to_string()));

    let detector_ref = Arc::new(FormatDetector::new(
        vec![json_parser, raw_parser],
        100,
        0.05,
        10000,
    ));

    let unknown_line = "This is a raw message without any special format";
    
    let enricher = Arc::new(EnrichmentPipeline::new());
    let masker = Arc::new(MaskingEngine::new());
    let router = Arc::new(ConditionalRouter::new("default".to_string()));

    let pipeline = LogPipeline::new(detector_ref, enricher, masker, router);

    let result = pipeline.process(unknown_line).await;

    assert!(result.is_ok(), "Pipeline error: {:?}", result);
    let event = result.unwrap();
    assert_eq!(event.parsed.parser_id, "raw");
}

#[test]
fn test_format_detection_cache_size_limit() {
    let json_parser = Arc::new(JsonParser::new("json".to_string(), None));
    let detector = FormatDetector::new(vec![json_parser], 5, 0.8, 10000);

    let lines = vec![
        r#"{"id":1}"#,
        r#"{"id":2}"#,
        r#"{"id":3}"#,
        r#"{"id":4}"#,
        r#"{"id":5}"#,
        r#"{"id":6}"#,
    ];

    for line in &lines {
        detector.detect(line);
    }

    let stats = detector.stats();
    assert_eq!(stats.total_lines_processed, 6);
}

#[test]
fn test_format_detector_stats() {
    let json_parser = Arc::new(JsonParser::new("json".to_string(), None));
    let detector = FormatDetector::new(vec![json_parser], 100, 0.8, 50);

    for i in 0..75 {
        let line = format!(r#"{{"id":{}}}"#, i);
        detector.detect(&line);
    }

    let stats = detector.stats();
    assert_eq!(stats.total_lines_processed, 75);
    assert_eq!(stats.lines_since_last_eval, 25);
}

#[tokio::test]
async fn test_pipeline_detector_reference() {
    let json_parser = Arc::new(JsonParser::new("json".to_string(), None));
    let detector = Arc::new(FormatDetector::new(vec![json_parser], 100, 0.8, 10000));
    let enricher = Arc::new(EnrichmentPipeline::new());
    let masker = Arc::new(MaskingEngine::new());
    let router = Arc::new(ConditionalRouter::new("default".to_string()));

    let pipeline = LogPipeline::new(detector, enricher, masker, router);

    let detector_ref = pipeline.detector();
    assert!(detector_ref.get_parser(0).is_some());
}

#[test]
fn test_format_detection_multiple_similar_formats() {
    let json_parser = Arc::new(JsonParser::new("json".to_string(), None));
    let detector = FormatDetector::new(vec![json_parser], 100, 0.8, 10000);

    let samples = vec![
        r#"{"type":"A","data":"value1"}"#,
        r#"{"type":"B","data":"value2"}"#,
        r#"{"type":"C","data":"value3"}"#,
    ];

    for sample in samples {
        let result = detector.detect(sample);
        assert!(result.is_some());
    }
}
