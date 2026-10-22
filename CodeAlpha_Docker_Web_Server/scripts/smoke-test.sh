#!/usr/bin/env bash
# Smoke tests for the running web server. Used locally and in CI.
# Usage: ./scripts/smoke-test.sh [base-url]
set -uo pipefail

BASE_URL="${1:-http://localhost:8080}"
failures=0

pass() { echo "  PASS  $1"; }
fail() { echo "  FAIL  $1"; failures=$((failures + 1)); }

expect_status() {
  local path="$1" expected="$2"
  local code
  code=$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL$path")
  if [ "$code" = "$expected" ]; then
    pass "GET $path -> $code"
  else
    fail "GET $path -> $code (expected $expected)"
  fi
}

expect_header() {
  local path="$1" header="$2"
  if curl -s -D - -o /dev/null "$BASE_URL$path" | grep -qi "^$header"; then
    pass "$path sends $header"
  else
    fail "$path is missing $header"
  fi
}

echo "Testing $BASE_URL"

expect_status "/" 200
expect_status "/health" 200
expect_status "/assets/style.css" 200
expect_status "/does-not-exist" 404
expect_status "/nginx_status" 403

if curl -s "$BASE_URL/" | grep -q "<title>CodeAlpha Web Server</title>"; then
  pass "home page has the expected title"
else
  fail "home page title not found"
fi

if curl -s "$BASE_URL/health" | grep -q '"status":"ok"'; then
  pass "/health body is {\"status\":\"ok\"}"
else
  fail "/health body is wrong"
fi

expect_header "/" "X-Content-Type-Options: nosniff"
expect_header "/" "X-Frame-Options: DENY"
expect_header "/" "Content-Security-Policy:"
expect_header "/assets/style.css" "Cache-Control: max-age="

if curl -s -D - -o /dev/null -H 'Accept-Encoding: gzip' "$BASE_URL/assets/style.css" | grep -qi '^Content-Encoding: gzip'; then
  pass "CSS is served gzip-compressed"
else
  fail "gzip compression is not working"
fi

if curl -s -D - -o /dev/null "$BASE_URL/" | grep -qi '^Server: nginx/[0-9]'; then
  fail "Server header leaks the nginx version"
else
  pass "Server header hides the nginx version"
fi

echo
if [ "$failures" -eq 0 ]; then
  echo "All checks passed."
else
  echo "$failures check(s) failed."
  exit 1
fi
