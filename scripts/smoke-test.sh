#!/usr/bin/env bash
# Smoke-tests the running docker compose stack, which uses the prod profile: probes, metrics, the split between the
# API and management ports, and JSON logs. These are the settings the integration tests (test profile) never load.
#
#   docker compose --profile full up -d --build
#   scripts/smoke-test.sh
#   docker compose --profile full down -v
set -euo pipefail

APP_URL=${APP_URL:-http://localhost:8080}
MANAGEMENT_URL=${MANAGEMENT_URL:-http://localhost:8081}
READY_TIMEOUT_SECONDS=${READY_TIMEOUT_SECONDS:-120}

fail() {
    echo "FAIL: $*" >&2
    exit 1
}

status_of() {
    curl -s -o /dev/null -w '%{http_code}' "$1"
}

echo "Waiting up to ${READY_TIMEOUT_SECONDS}s for readiness"
deadline=$((SECONDS + READY_TIMEOUT_SECONDS))
until curl -fsS "$MANAGEMENT_URL/actuator/health/readiness" 2>/dev/null | grep -q '"UP"'; do
    [ "$SECONDS" -lt "$deadline" ] || fail "not ready after ${READY_TIMEOUT_SECONDS}s"
    sleep 2
done

curl -fsS "$MANAGEMENT_URL/actuator/health/liveness" | grep -q '"UP"' || fail "liveness is not UP"

# a public request, so the HTTP and JDBC metrics below have something to report
curl -fsS "$APP_URL/api/products" > /dev/null || fail "GET /api/products failed"

metrics=$(curl -fsS "$MANAGEMENT_URL/actuator/prometheus") || fail "Prometheus scrape failed"
for metric in 'http_server_requests_seconds_count{application="orderhub"' 'jdbc_query_seconds_count{' 'hikaricp_connections_active{'; do
    grep -qF "$metric" <<< "$metrics" || fail "metric missing from scrape: $metric"
done

[ "$(status_of "$APP_URL/actuator/health")" != 200 ] || fail "actuator is reachable on the API port"
for endpoint in env heapdump loggers configprops; do
    [ "$(status_of "$MANAGEMENT_URL/actuator/$endpoint")" != 200 ] || fail "/actuator/$endpoint is exposed"
done

logs=$(docker compose logs --no-color --no-log-prefix app)
[ -n "$logs" ] || fail "the app wrote no logs"
non_json=$(jq -R -r 'select(length > 0) | select((try fromjson catch null) == null)' <<< "$logs")
[ -z "$non_json" ] || fail "log lines that are not JSON:"$'\n'"$non_json"
jq -e -s 'all(.service.name == "orderhub" and (.service.version | test("^[0-9]")))' <<< "$logs" > /dev/null \
    || fail "log lines without service.name or a filtered service.version"

echo "Smoke test passed"
