@echo off
setlocal enabledelayedexpansion

echo ========================================
echo Log Generator - 100K Events Test Suite
echo ========================================
echo.

REM Configuration for 100K logs
REM Using multiple files to stay within memory limits
REM 10 files x 10,000 logs per file = 100,000 total logs

set "OUTPUT_DIR=test\logs"
set "NUM_FILES=10"
set "LOGS_PER_FILE=10000"
set "INTERVAL_MS=100"

echo Configuration:
echo  - Output Directory: %OUTPUT_DIR%
echo  - Number of Files: %NUM_FILES%
echo  - Logs per File: %LOGS_PER_FILE%
echo  - Total Events: !NUM_FILES! x !LOGS_PER_FILE! = 100,000
echo  - Interval between files (ms): %INTERVAL_MS%
echo.

echo Building log generator binary...
cargo build --release --bin log_generator 2>nul
if errorlevel 1 (
    echo Error: Failed to build log generator
    echo Attempting to build without --release flag...
    cargo build --bin log_generator
    if errorlevel 1 (
        echo Failed to build log generator binary
        exit /b 1
    )
)

echo.
echo Starting log generation...
echo This will generate 100,000 logs across %NUM_FILES% files
echo.

REM Run the log generator
cargo run --release --bin log_generator -- %OUTPUT_DIR% %NUM_FILES% %LOGS_PER_FILE% %INTERVAL_MS%

if errorlevel 1 (
    echo Error: Log generation failed
    exit /b 1
)

echo.
echo ========================================
echo Log generation completed successfully!
echo ========================================
echo.
echo Next steps:
echo 1. Start the LogForwarder application
echo 2. The pipeline will process these 100K logs
echo 3. Monitor performance metrics:
echo    - Throughput (events/sec, bytes/sec)
echo    - Latency (p99 less than 10ms target)
echo    - CPU usage (target less than 5%%)
echo    - Memory consumption
echo 4. Check backpressure states transitions
echo 5. Verify batch processing and compression
echo.
pause
