#!/bin/bash
echo "=== Verifying PostgreSQL ==="
docker exec logforwarder-postgres psql -U logforwarder -d logforwarder -c "\dt" || exit 1

echo "=== Verifying ClickHouse ==="
docker exec logforwarder-clickhouse clickhouse-client --query "SHOW TABLES FROM logforwarder" || exit 1

echo "=== Verifying Middleware Health ==="
curl -f http://localhost:8080/actuator/health || exit 1

echo "✅ All databases healthy"
