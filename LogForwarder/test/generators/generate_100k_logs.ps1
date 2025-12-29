#!/usr/bin/env pwsh

param(
    [Parameter(Mandatory = $false)]
    [int]$TotalEvents = 100000,
    
    [Parameter(Mandatory = $false)]
    [int]$NumFiles = 10,
    
    [Parameter(Mandatory = $false)]
    [int]$IntervalMs = 100,
    
    [Parameter(Mandatory = $false)]
    [string]$OutputDir = "test\logs",
    
    [Parameter(Mandatory = $false)]
    [switch]$Release
)

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Log Generator - 100K Events Test Suite" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$logsPerFile = [math]::Ceiling($TotalEvents / $NumFiles)

Write-Host "Configuration:" -ForegroundColor Green
Write-Host "  Output Directory: $OutputDir"
Write-Host "  Number of Files: $NumFiles"
Write-Host "  Logs per File: $logsPerFile"
Write-Host "  Total Events: $NumFiles x $logsPerFile = $(($NumFiles * $logsPerFile))"
Write-Host "  Interval between files (ms): $IntervalMs"
Write-Host ""

Write-Host "Building log generator binary..." -ForegroundColor Yellow
$buildCmd = "cargo build --bin log_generator"
if ($Release) {
    $buildCmd += " --release"
}

Invoke-Expression $buildCmd | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Host "Error: Failed to build log generator" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "Starting log generation..." -ForegroundColor Yellow
Write-Host "This will generate $TotalEvents logs across $NumFiles files"
Write-Host ""

$runCmd = "cargo run"
if ($Release) {
    $runCmd += " --release"
}
$runCmd += " --bin log_generator -- $OutputDir $NumFiles $logsPerFile $IntervalMs"

Invoke-Expression $runCmd
if ($LASTEXITCODE -ne 0) {
    Write-Host "Error: Log generation failed" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Log generation completed successfully!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Green
Write-Host "1. Start the LogForwarder application"
Write-Host "2. The pipeline will process these $TotalEvents logs"
Write-Host "3. Monitor performance metrics:"
Write-Host "   - Throughput (events/sec, bytes/sec)"
Write-Host "   - Latency (p99 < 10ms target)"
Write-Host "   - CPU usage (target < 5%)"
Write-Host "   - Memory consumption"
Write-Host "4. Check backpressure states transitions"
Write-Host "5. Verify batch processing and compression"
Write-Host ""
Write-Host "Generated logs location: $OutputDir" -ForegroundColor Cyan
Write-Host ""
