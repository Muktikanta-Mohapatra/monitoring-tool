# Load Testing Script for Performance Validation
# Tests concurrent requests and monitors thread usage

param(
    [int]$NumRequests = 100,
    [int]$Concurrency = 10,
    [string]$Endpoint = "/actuator/metrics"
)

$baseUrl = "http://localhost:8080"
$results = @()
$startTime = Get-Date

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Starting Load Test" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Total Requests: $NumRequests" -ForegroundColor White
Write-Host "Concurrency Level: $Concurrency" -ForegroundColor White
Write-Host "Endpoint: $Endpoint" -ForegroundColor White
Write-Host "Start Time: $startTime" -ForegroundColor White
Write-Host ""

$responseTimes = @()
$successCount = 0
$errorCount = 0

# Simulate concurrent requests
for ($i = 1; $i -le $NumRequests; $i += $Concurrency) {
    $tasks = @()
    
    for ($j = 0; $j -lt $Concurrency -and ($i + $j) -le $NumRequests; $j++) {
        $taskBlock = {
            param($url, $requestNum)
            
            $reqStart = Get-Date
            try {
                $response = Invoke-WebRequest -Uri $url -UseBasicParsing -ErrorAction Stop
                $reqEnd = Get-Date
                $duration = ($reqEnd - $reqStart).TotalMilliseconds
                
                return @{
                    RequestNum = $requestNum
                    StatusCode = $response.StatusCode
                    Duration = $duration
                    Success = $true
                    Error = $null
                }
            }
            catch {
                $reqEnd = Get-Date
                $duration = ($reqEnd - $reqStart).TotalMilliseconds
                
                return @{
                    RequestNum = $requestNum
                    StatusCode = 0
                    Duration = $duration
                    Success = $false
                    Error = $_.Exception.Message
                }
            }
        }
        
        $job = Start-Job -ScriptBlock $taskBlock -ArgumentList "$baseUrl$Endpoint", ($i + $j)
        $tasks += $job
    }
    
    # Wait for all tasks to complete
    $taskResults = $tasks | Wait-Job | Receive-Job
    
    foreach ($result in $taskResults) {
        if ($result.Success) {
            $successCount++
        } else {
            $errorCount++
        }
        $responseTimes += $result.Duration
        $results += $result
    }
    
    $completedCount = $i + $Concurrency - 1
    if ($completedCount -gt $NumRequests) { $completedCount = $NumRequests }
    
    Write-Progress -Activity "Load Test" -Status "Completed: $completedCount/$NumRequests" -PercentComplete (($completedCount / $NumRequests) * 100)
}

$endTime = Get-Date
$totalDuration = ($endTime - $startTime).TotalSeconds

# Calculate statistics
$avgResponseTime = ($responseTimes | Measure-Object -Average).Average
$minResponseTime = ($responseTimes | Measure-Object -Minimum).Minimum
$maxResponseTime = ($responseTimes | Measure-Object -Maximum).Maximum
$stdDeviation = 0

if ($responseTimes.Count -gt 1) {
    $mean = $avgResponseTime
    $variance = ($responseTimes | ForEach-Object { [math]::Pow($_ - $mean, 2) } | Measure-Object -Average).Average
    $stdDeviation = [math]::Sqrt($variance)
}

$throughput = [math]::Round($NumRequests / $totalDuration, 2)

# Display Results
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Load Test Results" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Timing Summary:" -ForegroundColor Yellow
Write-Host "  Total Duration: $([math]::Round($totalDuration, 2)) seconds"
Write-Host "  Throughput: $throughput requests/second"
Write-Host ""

Write-Host "Response Time Statistics (ms):" -ForegroundColor Yellow
Write-Host "  Average: $([math]::Round($avgResponseTime, 2)) ms"
Write-Host "  Minimum: $([math]::Round($minResponseTime, 2)) ms"
Write-Host "  Maximum: $([math]::Round($maxResponseTime, 2)) ms"
Write-Host "  Std Dev: $([math]::Round($stdDeviation, 2)) ms"
Write-Host ""

Write-Host "Request Results:" -ForegroundColor Yellow
Write-Host "  Successful: $successCount/$NumRequests"
Write-Host "  Failed: $errorCount/$NumRequests"
Write-Host "  Success Rate: $([math]::Round(($successCount / $NumRequests) * 100, 2))%"
Write-Host ""

# Performance Assessment
Write-Host "Performance Assessment:" -ForegroundColor Yellow
if ($avgResponseTime -lt 100) {
    Write-Host "  Response Time: EXCELLENT (< 100ms avg)" -ForegroundColor Green
} elseif ($avgResponseTime -lt 500) {
    Write-Host "  Response Time: GOOD (< 500ms avg)" -ForegroundColor Green
} elseif ($avgResponseTime -lt 1000) {
    Write-Host "  Response Time: ACCEPTABLE (< 1000ms avg)" -ForegroundColor Yellow
} else {
    Write-Host "  Response Time: POOR (>= 1000ms avg)" -ForegroundColor Red
}

if ($successCount -eq $NumRequests) {
    Write-Host "  Reliability: EXCELLENT (100% success rate)" -ForegroundColor Green
} elseif ($successCount -ge ($NumRequests * 0.95)) {
    Write-Host "  Reliability: GOOD (95%+ success rate)" -ForegroundColor Green
} else {
    Write-Host "  Reliability: POOR (< 95% success rate)" -ForegroundColor Red
}

if ($throughput -gt 100) {
    Write-Host "  Throughput: EXCELLENT (> 100 req/sec)" -ForegroundColor Green
} elseif ($throughput -gt 50) {
    Write-Host "  Throughput: GOOD (> 50 req/sec)" -ForegroundColor Green
} else {
    Write-Host "  Throughput: ACCEPTABLE" -ForegroundColor Yellow
}

# Save detailed results
$results | Export-Csv -Path "target/load-test-results.csv" -NoTypeInformation
Write-Host ""
Write-Host "Detailed results saved to: target/load-test-results.csv" -ForegroundColor Cyan

# Summary to JSON
$summary = @{
    TestTime = $startTime.ToString("o")
    TotalDuration = [math]::Round($totalDuration, 2)
    NumRequests = $NumRequests
    SuccessCount = $successCount
    ErrorCount = $errorCount
    SuccessRate = [math]::Round(($successCount / $NumRequests) * 100, 2)
    Throughput = $throughput
    AvgResponseTime = [math]::Round($avgResponseTime, 2)
    MinResponseTime = [math]::Round($minResponseTime, 2)
    MaxResponseTime = [math]::Round($maxResponseTime, 2)
    StdDeviation = [math]::Round($stdDeviation, 2)
}

$summary | ConvertTo-Json | Set-Content -Path "target/load-test-summary.json"
Write-Host "Summary saved to: target/load-test-summary.json" -ForegroundColor Cyan

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Load Test Complete" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
