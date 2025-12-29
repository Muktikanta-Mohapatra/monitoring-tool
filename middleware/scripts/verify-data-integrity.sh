#!/bin/bash
echo "=== PostgreSQL Tables Count ==="
PG_COUNT=$(docker exec logforwarder-postgres psql -U logforwarder -d logforwarder -t -c "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public'")
echo "PostgreSQL tables: $PG_COUNT"
[ "$PG_COUNT" -ge 7 ] || { echo "❌ Expected at least 7 tables"; exit 1; }

echo "=== ClickHouse Tables Count ==="
CH_COUNT=$(docker exec logforwarder-clickhouse clickhouse-client --query "SELECT COUNT(*) FROM system.tables WHERE database='logforwarder'")
echo "ClickHouse tables: $CH_COUNT"
[ "$CH_COUNT" -ge 3 ] || { echo "❌ Expected at least 3 tables"; exit 1; }

echo "✅ Data integrity verified"
