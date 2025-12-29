#!/bin/bash

set -e

# Generate test batch data for performance testing
# Usage: ./generate-test-batch.sh [event_count] [output_file] [forwarder_id]

EVENT_COUNT="${1:-2000}"
OUTPUT_FILE="${2:-test-batch-${EVENT_COUNT}.json}"
FORWARDER_ID="${3:-test-forwarder-1}"

echo "Generating test batch with $EVENT_COUNT events..."
echo "Output file: $OUTPUT_FILE"
echo "Forwarder ID: $FORWARDER_ID"
echo ""

# Generate JSON with events
{
    echo "{"
    echo "  \"forwarderId\": \"$FORWARDER_ID\","
    echo "  \"events\": ["
    
    for ((i=1; i<=EVENT_COUNT; i++)); do
        # Generate varied event data
        SOURCE_NAME="source-$((RANDOM % 10 + 1))"
        SOURCE_TYPE="$([ $((RANDOM % 2)) -eq 0 ] && echo 'application' || echo 'system')"
        SEVERITY="$([ $((RANDOM % 3)) -eq 0 ] && echo 'ERROR' || ([ $((RANDOM % 2)) -eq 0 ] && echo 'WARNING' || echo 'INFO'))"
        RAW_DATA="Event #$i from $SOURCE_NAME: $(echo "Sample log data with various information" | sed "s/ /$(printf '\x00')/" | tr '\0' ' ')"
        
        # Add comma for all but the last event
        if [ $i -lt $EVENT_COUNT ]; then
            COMMA=","
        else
            COMMA=""
        fi
        
        cat << EOF
    {
      "sourceName": "$SOURCE_NAME",
      "sourcetype": "$SOURCE_TYPE",
      "severity": "$SEVERITY",
      "rawData": "$RAW_DATA",
      "rawMessage": "Log message for event $i",
      "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    }$COMMA
EOF
        
        # Progress indicator
        if [ $((i % 200)) -eq 0 ]; then
            echo "  Generated $i/$EVENT_COUNT events..." >&2
        fi
    done
    
    echo "  ]"
    echo "}"
} > "$OUTPUT_FILE"

# Verify output
EVENT_COUNT_IN_FILE=$(grep -o '\"sourceName\"' "$OUTPUT_FILE" | wc -l)

echo ""
echo "✓ Successfully generated $EVENT_COUNT_IN_FILE events"
echo "✓ File size: $(du -h "$OUTPUT_FILE" | cut -f1)"
echo "✓ Output: $OUTPUT_FILE"
echo ""

# Show sample
echo "Sample of first 500 bytes:"
echo "---"
head -c 500 "$OUTPUT_FILE"
echo ""
echo "---"
echo ""

# Validate JSON syntax
if command -v jq &> /dev/null; then
    echo "Validating JSON syntax..."
    if jq empty "$OUTPUT_FILE" 2>/dev/null; then
        echo "✓ JSON syntax is valid"
    else
        echo "✗ JSON syntax validation failed"
        exit 1
    fi
else
    echo "Note: jq not installed, JSON validation skipped"
fi

echo ""
echo "Test batch generation complete!"
echo "To test with this batch, use:"
echo "  curl -X POST http://localhost:8080/api/v1/events/batch \\"
echo "    -H 'Content-Type: application/json' \\"
echo "    -d @$OUTPUT_FILE"

exit 0
