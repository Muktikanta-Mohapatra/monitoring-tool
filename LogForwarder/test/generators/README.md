# 100K Log Generation Test Suite

This test suite generates 100,000 log events to test the lock-free event pipeline and batching system implemented in Milestone 5.

## Files

- **generate_100k_logs.bat** - Windows batch script for generating 100K logs
- **generate_100k_logs.ps1** - PowerShell script with advanced options

## Usage

### Batch File (Windows)
```cmd
cd P:\LogForwarder
test\generators\generate_100k_logs.bat
```

This will:
1. Generate 100,000 logs split across 10 files (10,000 logs per file)
2. Build the log generator binary
3. Place logs in `test\logs\` directory
4. Display next steps for testing

### PowerShell Script (Advanced)
```powershell
cd P:\LogForwarder
.\test\generators\generate_100k_logs.ps1 -TotalEvents 100000 -NumFiles 10 -Release
```

#### PowerShell Options
- `-TotalEvents` - Total number of logs to generate (default: 100000)
- `-NumFiles` - Number of files to split logs across (default: 10)
- `-IntervalMs` - Delay between file creation in milliseconds (default: 100)
- `-OutputDir` - Directory to output logs (default: test\logs)
- `-Release` - Build in release mode for faster execution

## Test Configuration

Default configuration generates:
- **10 files** with **10,000 logs each** = **100,000 total events**
- Each file has realistic log messages with timestamps and levels
- Files are created with 100ms intervals for realistic production simulation

## Expected Performance

When running LogForwarder with these generated logs, you should observe:
- **Throughput**: 100K+ events/second
- **Latency**: p99 < 10ms (typical: 1-5ms)
- **CPU Usage**: < 5% (lock-free implementation)
- **Memory**: Minimal growth (batch processing)

## Testing Steps

1. **Generate logs**:
   ```cmd
   test\generators\generate_100k_logs.bat
   ```

2. **Start LogForwarder**:
   ```cmd
   cargo run --release
   ```

3. **Monitor metrics** in logs:
   - Backpressure state transitions (GREEN → YELLOW → ORANGE → RED)
   - Event throughput tracking
   - Batch flush triggers (count, size, time-based)
   - Compression ratio

4. **Verify behavior**:
   - Check all logs were processed
   - Verify no events were dropped
   - Monitor CPU/memory under sustained load
   - Check queue depth monitoring

## Log Format

Generated logs follow this format:
```
[TIMESTAMP] [LOG_LEVEL] [Thread-X] Process event #Y - Request ID: REQ-XXXXXX - Status: OK
```

Example:
```
[2025-11-27 00:35:42.100] [INFO] [Thread-1] Process event #1 - Request ID: REQ-000001 - Status: OK
[2025-11-27 00:35:42.200] [ERROR] [Thread-2] Process event #2 - Request ID: REQ-000002 - Status: OK
[2025-11-27 00:35:42.300] [WARN] [Thread-3] Process event #3 - Request ID: REQ-000003 - Status: OK
```

## Troubleshooting

- **Script fails to find cargo**: Ensure Rust toolchain is installed and in PATH
- **Permission denied (PowerShell)**: Run: `Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser`
- **Build errors**: Run `cargo build --release --bin log_generator` manually for detailed error messages

## Notes

- The batch file will pause at the end to show completion status
- Both scripts will exit with error code 1 if generation fails
- Generated logs are persisted in `test/logs/` for multiple test runs
- Modify `NUM_FILES` and `LOGS_PER_FILE` in the batch file to test different scales
