#!/usr/bin/env bash
# Submits one or more recipe URLs to recipe-service's POST /api/scrape,
# which publishes a scrape-requested Kafka event. Scraping happens
# asynchronously; this script only confirms the request was accepted.
#
# /api/scrape requires a Supabase-issued JWT (@Authenticated), so this logs
# in with a password grant first and uses the returned access_token.
#
# Required env vars:
#   MP_EMAIL            Supabase user email
#   MP_PASSWORD         Supabase user password
#   SUPABASE_ANON_KEY   anon key for the Supabase project (local: `supabase status`)
#
# Optional env vars:
#   SUPABASE_URL   Supabase API URL (default: http://127.0.0.1:54321, i.e. local dev)
#   API_BASE_URL   recipe-service base URL (default: http://localhost:8080)
#
# Usage:
#   ./scripts/submit-recipe-scrape.sh <url> [url...]
#   printf '%s\n' <url1> <url2> | ./scripts/submit-recipe-scrape.sh -

set -euo pipefail

usage() {
  echo "Usage: $0 <url> [url...]" >&2
  echo "       printf '%s\n' <url1> <url2> | $0 -" >&2
}

if [[ $# -eq 0 ]]; then
  usage
  exit 1
fi

: "${MP_EMAIL:?Set MP_EMAIL to a Supabase user's email}"
: "${MP_PASSWORD:?Set MP_PASSWORD to that user's password}"
: "${SUPABASE_ANON_KEY:?Set SUPABASE_ANON_KEY (see \`supabase status\` for local dev)}"
: "${SUPABASE_URL:=http://127.0.0.1:54321}"
: "${API_BASE_URL:=http://localhost:8080}"

urls=()
if [[ "$1" == "-" ]]; then
  while IFS= read -r line; do
    [[ -n "$line" ]] && urls+=("$line")
  done
else
  urls=("$@")
fi

echo "Logging in to Supabase as $MP_EMAIL..." >&2
token_response=$(curl -sS -X POST "${SUPABASE_URL%/}/auth/v1/token?grant_type=password" \
  -H "apikey: $SUPABASE_ANON_KEY" \
  -H "Content-Type: application/json" \
  -d "$(jq -n --arg email "$MP_EMAIL" --arg password "$MP_PASSWORD" '{email: $email, password: $password}')")

access_token=$(jq -r '.access_token // empty' <<<"$token_response")
if [[ -z "$access_token" ]]; then
  echo "Login failed: $token_response" >&2
  exit 1
fi

status=0
tmp=$(mktemp)
trap 'rm -f "$tmp"' EXIT

for url in "${urls[@]}"; do
  echo -n "Submitting $url ... " >&2
  http_code=$(curl -sS -o "$tmp" -w '%{http_code}' -X POST "${API_BASE_URL%/}/api/scrape" \
    -H "Authorization: Bearer $access_token" \
    -H "Content-Type: application/json" \
    -d "$(jq -n --arg url "$url" '{url: $url}')")
  if [[ "$http_code" == "200" ]]; then
    echo "OK" >&2
  else
    echo "FAILED ($http_code): $(cat "$tmp")" >&2
    status=1
  fi
done

exit "$status"
