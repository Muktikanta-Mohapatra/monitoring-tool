#!/bin/bash
set -e

/entrypoint.sh "$@" &
CLICKHOUSE_PID=$!

echo "Waiting for ClickHouse to be ready..."
until clickhouse-client --user "${CLICKHOUSE_USER:-default}" --password "${CLICKHOUSE_PASSWORD:-}" --query "SELECT 1" > /dev/null 2>&1; do
    sleep 1
done

echo "ClickHouse is ready. Running initialization script..."
clickhouse-client --user "${CLICKHOUSE_USER:-default}" --password "${CLICKHOUSE_PASSWORD:-}" --multiquery < /docker-entrypoint-initdb.d/init.sql

echo "Initialization complete."
wait $CLICKHOUSE_PID
