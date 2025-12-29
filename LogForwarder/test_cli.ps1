#!/usr/bin/env pwsh
<#
    Test script for the new key=value CLI format
#>

$binaryPath = "P:\Web\monitoring-tool\LogForwarder\target\release\high-perf-forwarder.exe"
$logFile = "P:\Web\monitoring-tool\LogForwarder\test\generators\test\logs\app_1.log"
$apiKey = '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewY5GyYIq.Brub1u'

Write-Host "=== Test 1: Key=Value Format ===" -ForegroundColor Green
Write-Host "Command: $binaryPath logfile='$logFile' api-key='$apiKey'" -ForegroundColor Cyan
Write-Host "Starting forwarder (press Ctrl+C to stop)..." -ForegroundColor Yellow
Write-Host ""

# Test with timeout (10 seconds)
$job = Start-Job -ScriptBlock {
    $binaryPath = $args[0]
    $logFile = $args[1]
    $apiKey = $args[2]
    
    & $binaryPath "logfile=$logFile" "api-key=$apiKey"
} -ArgumentList $binaryPath, $logFile, $apiKey

# Wait for 5 seconds
Start-Sleep -Seconds 5

# Check if job is still running
if ($job.State -eq 'Running') {
    Write-Host "✅ Forwarder is running and processing logs!" -ForegroundColor Green
    Write-Host ""
    
    # Get job output
    $output = Receive-Job -Job $job -Keep 2>&1 | Select-Object -First 10
    Write-Host "Log output (first few lines):" -ForegroundColor Cyan
    $output | ForEach-Object { Write-Host $_ }
    
    # Stop the job
    Write-Host ""
    Write-Host "Stopping forwarder..." -ForegroundColor Yellow
    Stop-Job -Job $job
    Remove-Job -Job $job
} else {
    Write-Host "❌ Forwarder failed to start" -ForegroundColor Red
    Receive-Job -Job $job
    Remove-Job -Job $job
}

Write-Host ""
Write-Host "=== Test Complete ===" -ForegroundColor Green
