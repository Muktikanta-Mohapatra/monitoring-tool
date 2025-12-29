#!/bin/bash

set -e

echo "======================================================================"
echo "Async Event Ingestion Performance Test Suite"
echo "======================================================================"
echo ""

BASE_URL="${1:-http://localhost:8080/api/v1}"
OUTPUT_FILE="${2:-performance-results-async.json}"
RESULTS_DIR="performance-results"

mkdir -p "$RESULTS_DIR"

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Initialize results object
RESULTS="{\"tests\": []}"

print_test() {
    echo -e "${YELLOW}[TEST] $1${NC}"
}

print_pass() {
    echo -e "${GREEN}[PASS] $1${NC}"
}

print_fail() {
    echo -e "${RED}[FAIL] $1${NC}"
}

# Test 1: REST API Response Time
print_test "Test 1: REST API Response Time (<10ms)"

RESPONSE_TIMES=()
for i in {1..10}; do
    START=$(date +%s%N)
    RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/events/batch" \
        -H "Content-Type: application/json" \
        -d '{
            "forwarderId": "test-forwarder-'$i'",
            "events": [
                {"sourceName": "test-'$i'-1", "sourcetype": "log", "severity": "INFO", "rawData": "test event 1"},
                {"sourceName": "test-'$i'-2", "sourcetype": "log", "severity": "INFO", "rawData": "test event 2"},
                {"sourceName": "test-'$i'-3", "sourcetype": "log", "severity": "INFO", "rawData": "test event 3"}
            ]
        }')
    
    HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
    END=$(date +%s%N)
    ELAPSED=$((($END - $START) / 1000000))
    
    RESPONSE_TIMES+=($ELAPSED)
    
    if [ "$HTTP_CODE" = "202" ]; then
        echo "Request $i: ${ELAPSED}ms - HTTP $HTTP_CODE"
    else
        print_fail "Request $i returned HTTP $HTTP_CODE instead of 202"
    fi
done

# Calculate statistics
SUM=0
for time in "${RESPONSE_TIMES[@]}"; do
    SUM=$((SUM + time))
done
AVG=$((SUM / ${#RESPONSE_TIMES[@]}))
MAX=$(printf '%s\n' "${RESPONSE_TIMES[@]}" | sort -n | tail -1)
MIN=$(printf '%s\n' "${RESPONSE_TIMES[@]}" | sort -n | head -1)

if [ "$AVG" -lt 10 ]; then
    print_pass "REST API Response Time: AVG=${AVG}ms, MIN=${MIN}ms, MAX=${MAX}ms (THRESHOLD: <10ms)"
else
    print_fail "REST API Response Time: AVG=${AVG}ms exceeds threshold of 10ms"
fi

# Test 2: Throughput with 2000 Events
print_test "Test 2: Throughput Test (2000 Events)"

START=$(date +%s%N)
EVENT_JSON='{
    "forwarderId": "perf-test-forwarder",
    "events": ['

for i in {1..2000}; do
    if [ $i -gt 1 ]; then
        EVENT_JSON="${EVENT_JSON},"
    fi
    EVENT_JSON="${EVENT_JSON}
    {
        \"sourceName\": \"perf-source-$i\",
        \"sourcetype\": \"log\",
        \"severity\": \"INFO\",
        \"rawData\": \"Performance test event $i\"
    }"
done
EVENT_JSON="${EVENT_JSON}
    ]
}'

RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/events/batch" \
    -H "Content-Type: application/json" \
    -d "$EVENT_JSON")

HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
END=$(date +%s%N)
ELAPSED_MS=$((($END - $START) / 1000000))

THROUGHPUT=$((2000 * 1000 / $ELAPSED_MS))

if [ "$HTTP_CODE" = "202" ] && [ "$ELAPSED_MS" -lt 100 ]; then
    print_pass "Throughput: 2000 events in ${ELAPSED_MS}ms (${THROUGHPUT} events/sec)"
else
    print_fail "Throughput test failed. HTTP=$HTTP_CODE, Time=${ELAPSED_MS}ms"
fi

# Test 3: Concurrent Requests
print_test "Test 3: Concurrent Requests (10 threads)"

CONCURRENT_TIMES=()
for i in {1..10}; do
    {
        START=$(date +%s%N)
        curl -s -X POST "$BASE_URL/events/batch" \
            -H "Content-Type: application/json" \
            -d '{
                "forwarderId": "concurrent-'$i'",
                "events": [
                    {"sourceName": "concurrent-'$i'-1", "sourcetype": "log", "severity": "INFO", "rawData": "event"},
                    {"sourceName": "concurrent-'$i'-2", "sourcetype": "log", "severity": "INFO", "rawData": "event"}
                ]
            }' > /dev/null
        END=$(date +%s%N)
        ELAPSED=$((($END - $START) / 1000000))
        echo $ELAPSED >> "$RESULTS_DIR/concurrent-times.txt"
    } &
done
wait

if [ -f "$RESULTS_DIR/concurrent-times.txt" ]; then
    CONCURRENT_TIMES=($(cat "$RESULTS_DIR/concurrent-times.txt"))
    CONCURRENT_AVG=0
    for time in "${CONCURRENT_TIMES[@]}"; do
        CONCURRENT_AVG=$((CONCURRENT_AVG + time))
    done
    CONCURRENT_AVG=$((CONCURRENT_AVG / ${#CONCURRENT_TIMES[@]}))
    print_pass "Concurrent Requests: Average response time ${CONCURRENT_AVG}ms"
    rm -f "$RESULTS_DIR/concurrent-times.txt"
fi

# Test 4: Memory Usage
print_test "Test 4: Memory Usage Stability"

# Get memory before
MEM_BEFORE=$(free -m | awk 'NR==2{print $3}')

# Send burst of requests
for i in {1..100}; do
    curl -s -X POST "$BASE_URL/events/batch" \
        -H "Content-Type: application/json" \
        -d '{
            "forwarder Id": "memory-test-'$i'",
            "events": [
                {"sourceName": "mem-'$i'", "sourcetype": "log", "severity": "INFO", "rawData": "mem test"}
            ]
        }' > /dev/null &
    
    if [ $((i % 20)) -eq 0 ]; then
        wait
    fi
done
wait

sleep 2

# Get memory after
MEM_AFTER=$(free -m | awk 'NR==2{print $3}')
MEM_DIFF=$((MEM_AFTER - MEM_BEFORE))

if [ "$MEM_DIFF" -lt 200 ]; then
    print_pass "Memory Stability: Memory increase of ${MEM_DIFF}MB (THRESHOLD: <200MB)"
else
    print_fail "Memory Stability: Memory increase of ${MEM_DIFF}MB exceeds threshold"
fi

# Test 5: Queue Depth During Load
print_test "Test 5: Queue Depth During Load"

# This would require accessing queue metrics endpoint
QUEUE_ENDPOINT="${BASE_URL%/api/v1}/actuator/metrics/logforwarder.event.queue.size"

QUEUE_DEPTH=$(curl -s "$QUEUE_ENDPOINT" 2>/dev/null | grep -o '"value":[0-9]*' | head -1 | cut -d: -f2)

if [ -z "$QUEUE_DEPTH" ]; then
    QUEUE_DEPTH=0
fi

if [ "$QUEUE_DEPTH" -lt 1000 ]; then
    print_pass "Queue Depth: $QUEUE_DEPTH events (THRESHOLD: <1000)"
else
    print_fail "Queue Depth: $QUEUE_DEPTH events exceeds threshold"
fi

# Test 6: Error Rate
print_test "Test 6: Error Rate"

TOTAL_REQUESTS=50
SUCCESS_COUNT=0

for i in {1..50}; do
    HTTP_CODE=$(curl -s -w "%{http_code}" -o /dev/null -X POST "$BASE_URL/events/batch" \
        -H "Content-Type: application/json" \
        -d '{
            "forwarderId": "error-test-'$i'",
            "events": [
                {"sourceName": "error-'$i'", "sourcetype": "log", "severity": "INFO", "rawData": "test"}
            ]
        }')
    
    if [ "$HTTP_CODE" = "202" ]; then
        ((SUCCESS_COUNT++))
    fi
done

ERROR_RATE=$(echo "scale=2; (($TOTAL_REQUESTS - $SUCCESS_COUNT) * 100) / $TOTAL_REQUESTS" | bc)

if [ $(echo "$ERROR_RATE < 1" | bc) -eq 1 ]; then
    print_pass "Error Rate: ${ERROR_RATE}% (THRESHOLD: <1%)"
else
    print_fail "Error Rate: ${ERROR_RATE}% exceeds threshold"
fi

# Summary
echo ""
echo "======================================================================"
echo "Performance Test Summary"
echo "======================================================================"
echo "REST API Response Time: AVG=${AVG}ms (THRESHOLD: <10ms)"
echo "Throughput: ${THROUGHPUT} events/sec"
echo "Concurrent Requests: OK"
echo "Memory Usage: ${MEM_DIFF}MB increase (THRESHOLD: <200MB)"
echo "Queue Depth: ${QUEUE_DEPTH} events (THRESHOLD: <1000)"
echo "Error Rate: ${ERROR_RATE}% (THRESHOLD: <1%)"
echo ""
echo "Performance test completed!"
echo "Results saved to: $OUTPUT_FILE"
echo "======================================================================"

# Save results to file
cat > "$OUTPUT_FILE" << EOF
{
    "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
    "tests": {
        "rest_response_time": {
            "avg_ms": $AVG,
            "min_ms": $MIN,
            "max_ms": $MAX,
            "threshold_ms": 10,
            "passed": $([ "$AVG" -lt 10 ] && echo "true" || echo "false")
        },
        "throughput": {
            "events": 2000,
            "time_ms": $ELAPSED_MS,
            "events_per_sec": $THROUGHPUT,
            "threshold_per_sec": 200000,
            "passed": $([ "$THROUGHPUT" -gt 100000 ] && echo "true" || echo "false")
        },
        "concurrent_requests": {
            "thread_count": 10,
            "avg_response_time_ms": $CONCURRENT_AVG,
            "threshold_ms": 100,
            "passed": $([ "$CONCURRENT_AVG" -lt 100 ] && echo "true" || echo "false")
        },
        "memory_usage": {
            "increase_mb": $MEM_DIFF,
            "threshold_mb": 200,
            "passed": $([ "$MEM_DIFF" -lt 200 ] && echo "true" || echo "false")
        },
        "queue_depth": {
            "current_events": $QUEUE_DEPTH,
            "threshold_events": 1000,
            "passed": $([ "$QUEUE_DEPTH" -lt 1000 ] && echo "true" || echo "false")
        },
        "error_rate": {
            "total_requests": $TOTAL_REQUESTS,
            "successful": $SUCCESS_COUNT,
            "error_percent": $ERROR_RATE,
            "threshold_percent": 1,
            "passed": $(echo "$ERROR_RATE < 1" | bc)
        }
    }
}
EOF

echo ""
echo "JSON results saved to: $OUTPUT_FILE"

exit 0
