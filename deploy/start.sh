#!/usr/bin/env bash
set -euo pipefail

database_pid=
backend_pid=
nginx_pid=
database_ready=

shutdown() {
    if [[ -n "$nginx_pid" ]]; then
        kill -QUIT "$nginx_pid" 2>/dev/null || true
        wait "$nginx_pid" || true
    fi
    if [[ -n "$backend_pid" ]]; then
        kill -TERM "$backend_pid" 2>/dev/null || true
        wait "$backend_pid" || true
    fi
    if [[ -n "$database_pid" ]]; then
        if [[ -n "$database_ready" ]]; then
            kill -INT "$database_pid" 2>/dev/null || true
        else
            kill -TERM "$database_pid" 2>/dev/null || true
        fi
        wait "$database_pid" || true
    fi
}

trap shutdown EXIT
trap 'exit 0' TERM INT

install -d -o node -g node "$WHZNT_STORAGE_DIR"
install -d "${PGDATA%/*}"
password_file="${PGDATA%/*}/.whznt-db-password"
if [[ ! -s "$password_file" ]]; then
    if [[ -s "$PGDATA/PG_VERSION" ]]; then
        echo "WHZNT: The database volume is missing its password file." >&2
        exit 1
    fi
    password=$(node -p 'require("node:crypto").randomBytes(32).toString("hex")')
    (umask 077; printf '%s\n' "$password" > "$password_file")
fi
database_password=$(< "$password_file")

POSTGRES_USER=whznt POSTGRES_DB=whznt POSTGRES_PASSWORD="$database_password" \
    postgres-entrypoint postgres -c listen_addresses=127.0.0.1 &
database_pid=$!

deadline=$((SECONDS + 60))
until pg_isready -q -h 127.0.0.1 -t 1 -U whznt -d whznt; do
    if ! kill -0 "$database_pid" 2>/dev/null || (( SECONDS >= deadline )); then
        echo "WHZNT: PostgreSQL did not become ready." >&2
        exit 1
    fi
    sleep 1
done
database_ready=1

HOME=/home/node SERVER_ADDRESS=127.0.0.1 SPRING_DATASOURCE_PASSWORD="$database_password" \
    gosu node java -Dfile.encoding=UTF-8 -Djava.awt.headless=true -jar /app/whznt.jar &
backend_pid=$!

nginx -g 'daemon off;' &
nginx_pid=$!

if wait -n "$database_pid" "$backend_pid" "$nginx_pid"; then
    echo "WHZNT: A service exited unexpectedly." >&2
fi
exit 1
