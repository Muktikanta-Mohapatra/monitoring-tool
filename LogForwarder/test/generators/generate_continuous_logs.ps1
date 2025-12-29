#!/usr/bin/env pwsh

param(
    [Parameter(Mandatory = $false)]
    [int]$NumFiles = 3,
    
    [Parameter(Mandatory = $false)]
    [int]$LogsPerCycle = 100,
    
    [Parameter(Mandatory = $false)]
    [int]$IntervalMs = 100,
    
    [Parameter(Mandatory = $false)]
    [string]$OutputDir = "test\logs",
    
    [Parameter(Mandatory = $false)]
    [switch]$Release
)

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Continuous Log Generator" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "Configuration:" -ForegroundColor Green
Write-Host "  Output Directory: $OutputDir"
Write-Host "  Number of Files: $NumFiles"
Write-Host "  Logs per Cycle: $LogsPerCycle"
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
Write-Host "Starting continuous log generation..." -ForegroundColor Yellow
Write-Host "Press Ctrl+C to stop" -ForegroundColor Cyan
Write-Host ""

$runCmd = "cargo run"
if ($Release) {
    $runCmd += " --release"
}
$runCmd += " --bin log_generator -- $OutputDir $NumFiles $LogsPerCycle $IntervalMs --continuous"

Invoke-Expression $runCmd
if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host "Log generation stopped" -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Cyan
}
Write-Host ""
Write-Host "Generated logs location: $OutputDir" -ForegroundColor Cyan
Write-Host ""
