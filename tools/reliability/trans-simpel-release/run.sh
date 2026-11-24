#!/usr/bin/env bash
set -euo pipefail

source_dir="$(realpath "${1:?Path ke sumber server wajib diisi}")"
manifest="$(realpath -m "${2:-transiva-tested-server.txt}")"
fixture_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
workspace="$(mktemp -d)"
web_pid=''
trap 'if [[ -n "$web_pid" ]]; then kill "$web_pid" 2>/dev/null || true; fi; rm -rf "$workspace"' EXIT
export TRANSIVA_TEST_DB_PORT="${TRANSIVA_TEST_DB_PORT:-3306}"
export TRANSIVA_TEST_PHP="${TRANSIVA_TEST_PHP:-php}"
export TRANSIVA_TEST_MYSQL="${TRANSIVA_TEST_MYSQL:-$(command -v mariadb || command -v mysql)}"
export MYSQL_PWD="${TRANSIVA_TEST_DB_PASSWORD:-}"
client=("$TRANSIVA_TEST_MYSQL" --no-defaults -h127.0.0.1 --port="$TRANSIVA_TEST_DB_PORT" -uroot)

mkdir -p "$workspace/web" "$workspace/tmp/transiva_route_v2"
for file in schema.sql test.py worker.php media-mime.php routecache.php; do
    cp "$fixture_dir/$file" "$workspace/$file"
done
cp -a "$source_dir/." "$workspace/web/"
cp "$fixture_dir/fixture-config.php" "$workspace/web/config.php"
cd "$workspace"
uuid_hash=$(python3 -c 'import hmac; print(hmac.new(b"local-courier-fixture",b"11111111-1111-4111-8111-111111111111","sha256").hexdigest())')
# Use a fresh CI DB service. Do not overwrite an existing database.
sed "s/__UUID_HASH__/$uuid_hash/g" schema.sql | "${client[@]}"
"$TRANSIVA_TEST_PHP" -r 'define("TRANSIVA_SCHEMA_DEPLOYMENT",true); require "web/config.php"; require "web/transiva_regional_pricing.php"; trp_ensure_schema($conn);'
"${client[@]}" courier_test < web/migrations/trans_simpel.sql
"${client[@]}" courier_test -e "DELETE FROM transiva_regions; INSERT INTO transiva_regions(id,code,name,center_lat,center_lng,coverage_km,is_active,sort_order) VALUES(1,'LOCAL','Regional Palu',-0.9,119.9,32,1,0),(2,'OTHER','Regional Lain',-0.9,120.2,32,1,1);"
"$TRANSIVA_TEST_PHP" web/migrations/upgrade_call_reliability.php
"${client[@]}" courier_test < web/migrations/trans_simpel_ride.sql
"${client[@]}" courier_test < web/migrations/tiva_simpel_handoff.sql
"${client[@]}" courier_test < web/migrations/upgrade_order_location_corrections.sql
"$TRANSIVA_TEST_PHP" routecache.php

"$TRANSIVA_TEST_PHP" -d "sys_temp_dir=$workspace/tmp" -d "auto_prepend_file=$workspace/media-mime.php" \
    -S 127.0.0.1:18349 -t web > http.log 2>&1 &
web_pid=$!
for i in $(seq 1 40); do
    if curl -s -o /dev/null http://127.0.0.1:18349/customer_transsend_couriers.php; then break; fi
    sleep .15
done
if ! python3 test.py; then
    tail -150 http.log
    exit 1
fi
{
    printf 'Customer commit %s\n' "${GITHUB_SHA:-local}"
    printf 'Database configuration supplied by the fixture\n'
    (
        cd "$source_dir"
        find . -type f \( -name '*.php' -o -name '*.sql' \) ! -path './config.php' \
            -print0 | LC_ALL=C sort -z | xargs -0 sha256sum
    )
} > "$manifest"
