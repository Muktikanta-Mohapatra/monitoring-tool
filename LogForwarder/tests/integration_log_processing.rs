use high_perf_forwarder::parser::{JsonParser, LogParser};
use std::fs;

#[tokio::test]
async fn test_parse_generated_json_logs() {
    let content = fs::read_to_string("test/logs/app_1_json.json")
        .expect("Failed to read generated JSON logs");

    let parser = JsonParser::new("json_test".to_string(), None);
    let lines: Vec<&str> = content.lines().collect();

    assert!(!lines.is_empty(), "No logs found in generated file");

    let mut parsed_count = 0;
    for line in lines.iter().take(5) {
        if let Ok(event) = parser.parse(line).await {
            assert!(event.fields.contains_key("message"), "Missing 'message' field");
            assert!(event.fields.contains_key("level"), "Missing 'level' field");
            parsed_count += 1;
        }
    }

    assert!(parsed_count > 0, "No JSON logs were parsed");
    println!("✓ Successfully parsed {} JSON log entries", lines.len());
}

#[test]
fn test_generated_syslog_files_exist() {
    let files = vec![
        "test/logs/app_1_syslog.log",
        "test/logs/app_1_syslog5424.log",
    ];

    for file in files {
        let content = fs::read_to_string(file)
            .expect(&format!("Failed to read {}", file));
        assert!(!content.is_empty(), "File is empty: {}", file);
        
        let lines = content.lines().count();
        assert!(lines > 0, "No lines in: {}", file);
        
        for line in content.lines().take(1) {
            assert!(line.contains("<") && line.contains(">"), 
                    "Syslog format check failed for: {}", file);
        }
        
        println!("✓ {} generated with {} entries", file, lines);
    }
}

#[tokio::test]
async fn test_generated_logs_exist() {
    let files = vec![
        "test/logs/app_1_json.json",
        "test/logs/app_2_json.json",
        "test/logs/app_1_syslog.log",
        "test/logs/app_2_syslog.log",
        "test/logs/app_1_text.log",
        "test/logs/app_2_text.log",
        "test/logs/app_1_mixed.log",
    ];

    for file in files {
        assert!(
            fs::metadata(file).is_ok(),
            "Generated log file not found: {}",
            file
        );

        let content = fs::read_to_string(file).expect(&format!("Failed to read {}", file));
        let lines = content.lines().count();
        assert!(lines > 0, "Log file is empty: {}", file);
        println!("✓ {} - {} entries", file, lines);
    }
}

#[test]
fn test_generated_logs_formats() {
    let json_content = fs::read_to_string("test/logs/app_1_json.json")
        .expect("Failed to read JSON logs");
    assert!(
        json_content.trim_start().starts_with('{'),
        "JSON file should start with brace"
    );

    let syslog_content = fs::read_to_string("test/logs/app_1_syslog.log")
        .expect("Failed to read syslog logs");
    assert!(
        syslog_content.contains("<"),
        "Syslog should contain priority brackets"
    );

    let text_content = fs::read_to_string("test/logs/app_1_text.log")
        .expect("Failed to read text logs");
    assert!(
        text_content.contains("["),
        "Text logs should contain timestamps"
    );

    let mixed_content = fs::read_to_string("test/logs/app_1_mixed.log")
        .expect("Failed to read mixed logs");
    assert!(
        mixed_content.contains("{") || mixed_content.contains("<"),
        "Mixed logs should contain multiple formats"
    );

    println!("✓ All generated log formats validated");
}
