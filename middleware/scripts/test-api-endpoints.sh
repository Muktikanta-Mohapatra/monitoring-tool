#!/bin/bash
BASE_URL="http://localhost:8080/api/v1"

# Test authentication (PostgreSQL)
echo "Testing login..."
TOKEN=$(curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"forwarder-secret-key-001"}' \
  | jq -r '.accessToken')

[ -n "$TOKEN" ] || { echo "❌ Login failed"; exit 1; }
echo "✅ Login successful"

# Test forwarder list (PostgreSQL)
echo "Testing forwarders endpoint..."
curl -f -H "Authorization: Bearer $TOKEN" "$BASE_URL/forwarders" > /dev/null || { echo "❌ Forwarders endpoint failed"; exit 1; }
echo "✅ Forwarders endpoint working"

# Test events (ClickHouse)
echo "Testing events endpoint..."
curl -f -H "Authorization: Bearer $TOKEN" "$BASE_URL/events?limit=10" > /dev/null || { echo "❌ Events endpoint failed"; exit 1; }
echo "✅ Events endpoint working"

echo "✅ All API endpoints working"
