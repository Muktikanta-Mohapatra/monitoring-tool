use chrono::Local;
use serde_json::json;
use std::fs::{self, File, OpenOptions};
use std::io::Write;
use std::path::Path;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::thread;
use std::time::Duration;

#[derive(Debug, Clone, Copy, PartialEq)]
enum LogFormat {
    Text,
    Json,
    Syslog,
    Syslog5424,
    Csv,
    ApacheAccess,
    Windows,
    Mixed,
}

impl LogFormat {
    fn from_str(s: &str) -> Option<Self> {
        match s.to_lowercase().as_str() {
            "text" => Some(LogFormat::Text),
            "json" => Some(LogFormat::Json),
            "syslog" => Some(LogFormat::Syslog),
            "syslog5424" => Some(LogFormat::Syslog5424),
            "csv" => Some(LogFormat::Csv),
            "apache" => Some(LogFormat::ApacheAccess),
            "windows" => Some(LogFormat::Windows),
            "mixed" => Some(LogFormat::Mixed),
            _ => None,
        }
    }

    fn extension(&self) -> &str {
        match self {
            LogFormat::Text => "log",
            LogFormat::Json => "json",
            LogFormat::Syslog => "log",
            LogFormat::Syslog5424 => "log",
            LogFormat::Csv => "csv",
            LogFormat::ApacheAccess => "access",
            LogFormat::Windows => "evtx",
            LogFormat::Mixed => "log",
        }
    }
}

fn generate_text_log(line_num: u64, timestamp: chrono::DateTime<Local>) -> String {
    let log_level = match line_num % 5 {
        1 => "ERROR",
        2 => "WARN",
        3 => "DEBUG",
        _ => "INFO",
    };
    format!(
        "[{}] [{}] [Thread-{}] Process event #{} - Request ID: REQ-{:06} - Status: OK",
        timestamp.format("%Y-%m-%d %H:%M:%S%.3f"),
        log_level,
        (line_num % 4) + 1,
        line_num,
        line_num
    )
}

fn generate_json_log(line_num: u64, timestamp: chrono::DateTime<Local>) -> String {
    let log_level = match line_num % 5 {
        1 => "ERROR",
        2 => "WARN",
        3 => "DEBUG",
        _ => "INFO",
    };
    let status_code = match line_num % 3 {
        0 => 200,
        1 => 404,
        _ => 500,
    };
    let log_obj = json!({
        "timestamp": timestamp.to_rfc3339(),
        "level": log_level,
        "message": format!("Process event #{}", line_num),
        "request_id": format!("REQ-{:06}", line_num),
        "thread_id": (line_num % 4) + 1,
        "status_code": status_code,
        "duration_ms": (line_num as i32 % 1000) + 10,
        "service": "app-service",
        "environment": "production"
    });
    log_obj.to_string()
}

fn generate_syslog_log(line_num: u64, timestamp: chrono::DateTime<Local>) -> String {
    let priority = match line_num % 5 {
        1 => 11,
        2 => 12,
        3 => 15,
        _ => 14,
    };
    let hostname = format!("server-{}", (line_num % 10) + 1);
    let process_id = 1000 + (line_num % 100);
    format!(
        "<{}>{} {} app[{}]: Process event #{} - Request ID: REQ-{:06}",
        priority,
        timestamp.format("%b %d %H:%M:%S"),
        hostname,
        process_id,
        line_num,
        line_num
    )
}

fn generate_syslog5424_log(line_num: u64, timestamp: chrono::DateTime<Local>) -> String {
    let priority = match line_num % 5 {
        1 => 11,
        2 => 12,
        3 => 15,
        _ => 14,
    };
    let hostname = format!("server-{}", (line_num % 10) + 1);
    let msg_id = format!("msg-{:06}", line_num);
    let process_id = 1000 + (line_num % 100);
    format!(
        "<{}> 1 {} {} app {} {} - - Process event #{} - Status: OK",
        priority,
        timestamp.to_rfc3339(),
        hostname,
        process_id,
        msg_id,
        line_num
    )
}

fn generate_csv_log(line_num: u64, timestamp: chrono::DateTime<Local>) -> String {
    let log_level = match line_num % 5 {
        1 => "ERROR",
        2 => "WARN",
        3 => "DEBUG",
        _ => "INFO",
    };
    let status_code = match line_num % 3 {
        0 => "200",
        1 => "404",
        _ => "500",
    };
    let duration = (line_num as i32 % 1000) + 10;
    format!(
        "{},{},{},{},REQ-{:06},Thread-{},{},{}",
        timestamp.format("%Y-%m-%d %H:%M:%S%.3f"),
        log_level,
        line_num,
        "app-service",
        line_num,
        (line_num % 4) + 1,
        status_code,
        duration
    )
}

fn generate_apache_access_log(line_num: u64, timestamp: chrono::DateTime<Local>) -> String {
    let methods = ["GET", "POST", "PUT", "DELETE"];
    let paths = [
        "/api/users",
        "/api/products",
        "/health",
        "/metrics",
        "/api/orders",
    ];
    let status_codes = [200, 201, 400, 404, 500];
    let user_agents = [
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36",
        "curl/7.64.1",
        "PostmanRuntime/7.26.5",
    ];

    let method = methods[(line_num as usize) % methods.len()];
    let path = paths[(line_num as usize) % paths.len()];
    let status = status_codes[(line_num as usize) % status_codes.len()];
    let ip = format!(
        "192.168.{}.{}",
        (line_num / 256) % 256,
        line_num % 256
    );
    let bytes_sent = 512 + ((line_num * 1234) % 10240);
    let user_agent = user_agents[(line_num as usize) % user_agents.len()];

    format!(
        "{} - - [{}] \"{} {} HTTP/1.1\" {} {} \"-\" \"{}\"",
        ip,
        timestamp.format("%d/%b/%Y:%H:%M:%S %z"),
        method,
        path,
        status,
        bytes_sent,
        user_agent
    )
}

fn generate_windows_log(line_num: u64, timestamp: chrono::DateTime<Local>) -> String {
    let event_types = ["Information", "Warning", "Error", "Critical"];
    let sources = ["Application", "System", "Security", "PowerShell"];
    let event_type = event_types[(line_num as usize) % event_types.len()];
    let source = sources[(line_num as usize) % sources.len()];
    let event_id = 1000 + (line_num % 100);

    format!(
        "Type: {} Source: {} Event ID: {} Time: {} Description: Process event #{} - Request ID: REQ-{:06}",
        event_type,
        source,
        event_id,
        timestamp.format("%Y-%m-%d %H:%M:%S"),
        line_num,
        line_num
    )
}

fn generate_test_logs(
    output_dir: &str,
    num_files: usize,
    logs_per_file: usize,
    interval_ms: u64,
    continuous: bool,
    format: LogFormat,
) -> std::io::Result<()> {
    fs::create_dir_all(output_dir)?;
    println!("Generating test logs in: {} (Format: {:?})", output_dir, format);

    let running = Arc::new(AtomicBool::new(true));
    let running_clone = Arc::clone(&running);

    ctrlc::set_handler(move || {
        println!("\n\nShutting down log generator...");
        running_clone.store(false, Ordering::SeqCst);
    })
    .expect("Error setting Ctrl-C handler");

    if continuous {
        println!("Continuous log generation mode - Press Ctrl+C to stop");
        println!();

        let mut event_counter = 0u64;
        let mut cycle = 1u64;

        while running.load(Ordering::SeqCst) {
            for file_num in 1..=num_files {
                if !running.load(Ordering::SeqCst) {
                    break;
                }

                let ext = format.extension();
                let file_path =
                    Path::new(output_dir).join(format!("app_{}_{}.{}", file_num, format!("{:?}",format).to_lowercase(), ext));
                let mut file = OpenOptions::new()
                    .create(true)
                    .append(true)
                    .open(&file_path)?;

                let mut csv_header_written = false;

                for _ in 1..=logs_per_file {
                    if !running.load(Ordering::SeqCst) {
                        break;
                    }

                    event_counter += 1;
                    let timestamp = Local::now();

                    if format == LogFormat::Csv && !csv_header_written {
                        file.write_all(b"timestamp,level,event_num,service,request_id,thread,status,duration_ms\n")?;
                        csv_header_written = true;
                    }

                    let log_line = match format {
                        LogFormat::Text => generate_text_log(event_counter, timestamp),
                        LogFormat::Json => generate_json_log(event_counter, timestamp),
                        LogFormat::Syslog => generate_syslog_log(event_counter, timestamp),
                        LogFormat::Syslog5424 => generate_syslog5424_log(event_counter, timestamp),
                        LogFormat::Csv => generate_csv_log(event_counter, timestamp),
                        LogFormat::ApacheAccess => generate_apache_access_log(event_counter, timestamp),
                        LogFormat::Windows => generate_windows_log(event_counter, timestamp),
                        LogFormat::Mixed => {
                            match event_counter % 5 {
                                0 => generate_json_log(event_counter, timestamp),
                                1 => generate_syslog_log(event_counter, timestamp),
                                2 => generate_apache_access_log(event_counter, timestamp),
                                3 => generate_text_log(event_counter, timestamp),
                                _ => generate_windows_log(event_counter, timestamp),
                            }
                        }
                    };

                    file.write_all(log_line.as_bytes())?;
                    file.write_all(b"\n")?;
                    file.flush()?;
                }

                print!(".");
                std::io::stdout().flush()?;
                thread::sleep(Duration::from_millis(interval_ms));
            }
            println!(" [Cycle {}]", cycle);
            cycle += 1;
        }

        println!(
            "✓ Log generation stopped. Total events generated: {}",
            event_counter
        );
    } else {
        for file_num in 1..=num_files {
            let ext = format.extension();
            let file_path =
                Path::new(output_dir).join(format!("app_{}_{}.{}", file_num, format!("{:?}",format).to_lowercase(), ext));
            let mut file = File::create(&file_path)?;
            let start_time = Local::now();

            println!(
                "Creating {} with {} log entries...",
                file_path.display(),
                logs_per_file
            );

            let mut csv_header_written = false;

            for line_num in 1..=logs_per_file {
                let line_num_u64 = line_num as u64;
                let timestamp =
                    start_time + chrono::Duration::milliseconds((line_num as i64) * 100);

                if format == LogFormat::Csv && !csv_header_written {
                    file.write_all(b"timestamp,level,event_num,service,request_id,thread,status,duration_ms\n")?;
                    csv_header_written = true;
                }

                let log_line = match format {
                    LogFormat::Text => generate_text_log(line_num_u64, timestamp),
                    LogFormat::Json => generate_json_log(line_num_u64, timestamp),
                    LogFormat::Syslog => generate_syslog_log(line_num_u64, timestamp),
                    LogFormat::Syslog5424 => generate_syslog5424_log(line_num_u64, timestamp),
                    LogFormat::Csv => generate_csv_log(line_num_u64, timestamp),
                    LogFormat::ApacheAccess => generate_apache_access_log(line_num_u64, timestamp),
                    LogFormat::Windows => generate_windows_log(line_num_u64, timestamp),
                    LogFormat::Mixed => {
                        match line_num_u64 % 5 {
                            0 => generate_json_log(line_num_u64, timestamp),
                            1 => generate_syslog_log(line_num_u64, timestamp),
                            2 => generate_apache_access_log(line_num_u64, timestamp),
                            3 => generate_text_log(line_num_u64, timestamp),
                            _ => generate_windows_log(line_num_u64, timestamp),
                        }
                    }
                };

                file.write_all(log_line.as_bytes())?;
                file.write_all(b"\n")?;
            }

            println!("✓ Created: {}", file_path.display());
            if file_num < num_files {
                thread::sleep(Duration::from_millis(interval_ms));
            }
        }

        println!("\n✓ All test logs generated successfully!");
    }

    Ok(())
}

fn main() -> std::io::Result<()> {
    let args: Vec<String> = std::env::args().collect();

    let output_dir = if args.len() > 1 {
        &args[1]
    } else {
        "./test/logs"
    };

    let num_files = if args.len() > 2 {
        args[2].parse::<usize>().unwrap_or(3)
    } else {
        3
    };

    let logs_per_file = if args.len() > 3 {
        args[3].parse::<usize>().unwrap_or(100)
    } else {
        100
    };

    let interval_ms = if args.len() > 4 {
        args[4].parse::<u64>().unwrap_or(500)
    } else {
        500
    };

    let format_str = if args.len() > 5 {
        &args[5]
    } else {
        "text"
    };

    let format = LogFormat::from_str(format_str)
        .unwrap_or_else(|| {
            eprintln!("Unknown format: {}. Using 'text' by default.", format_str);
            LogFormat::Text
        });

    let continuous = args.contains(&"--continuous".to_string()) || args.contains(&"-c".to_string());

    println!("Log Generator Configuration:");
    println!("  Output Directory: {}", output_dir);
    println!("  Number of Files: {}", num_files);
    println!("  Logs per File: {}", logs_per_file);
    println!("  Interval between files (ms): {}", interval_ms);
    println!("  Format: {:?}", format);
    println!(
        "  Mode: {}",
        if continuous { "Continuous" } else { "One-time" }
    );
    println!("\nSupported formats: text, json, syslog, syslog5424, csv, apache, windows, mixed");
    println!();

    generate_test_logs(
        output_dir,
        num_files,
        logs_per_file,
        interval_ms,
        continuous,
        format,
    )?;

    Ok(())
}
