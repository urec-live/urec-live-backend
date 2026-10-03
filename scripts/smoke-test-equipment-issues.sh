#!/usr/bin/env bash
# Smoke test for equipment issue reporting against a RUNNING backend.
#
# Walks the real member -> admin -> member loop over HTTP and prints PASS/FAIL for each check,
# including taking the machine out of service and back. It writes real data (one issue report,
# resolved again at the end, plus activity-log entries) and briefly marks the machine out of order,
# so point it at a local or dev database, never production.
#
# Usage (Git Bash, macOS or Linux), from the repo root:
#   MEMBER_USER=alice MEMBER_PASS='secret' ADMIN_USER=admin ADMIN_PASS='secret' \
#     ./scripts/smoke-test-equipment-issues.sh
#
# Optional:
#   API=http://localhost:8080/api   backend base URL (default shown)
#   EQUIPMENT_ID=3                  machine to report (default: first machine from GET /machines)

set -u

API="${API:-http://localhost:8080/api}"
: "${MEMBER_USER:?Set MEMBER_USER to a regular member account}"
: "${MEMBER_PASS:?Set MEMBER_PASS}"
: "${ADMIN_USER:?Set ADMIN_USER to an account with the ADMIN role}"
: "${ADMIN_PASS:?Set ADMIN_PASS}"

passed=0
failed=0
pass() { printf '  \033[32mPASS\033[0m %s\n' "$1"; passed=$((passed + 1)); }
fail() { printf '  \033[31mFAIL\033[0m %s\n' "$1"; failed=$((failed + 1)); }

summary() {
  echo
  echo "$passed passed, $failed failed"
  [ "$failed" -eq 0 ]
}

# request METHOD PATH [TOKEN] [JSON] -> sets STATUS and BODY
request() {
  local method="$1" path="$2" token="${3:-}" data="${4:-}"
  local args=(-s -w $'\n%{http_code}' -X "$method" "$API$path" -H 'Content-Type: application/json')
  [ -n "$token" ] && args+=(-H "Authorization: Bearer $token")
  [ -n "$data" ] && args+=(-d "$data")
  local response
  if ! response=$(curl "${args[@]}"); then
    STATUS=000
    BODY=''
    return
  fi
  STATUS="${response##*$'\n'}"
  BODY="${response%$'\n'*}"
}

expect_status() { # EXPECTED DESCRIPTION
  if [ "$STATUS" = "$1" ]; then pass "$2 ($STATUS)"; else fail "$2 (expected $1, got $STATUS)"; fi
}

json_escape() { sed 's/\\/\\\\/g; s/"/\\"/g' <<< "$1"; }
json_string() { sed -n "s/.*\"$1\":\"\([^\"]*\)\".*/\1/p" <<< "$2" | head -1; }
json_number() { grep -o "\"$1\":[0-9]*" <<< "$2" | head -1 | cut -d: -f2; }

# One JSON object per line, so a report's fields can be matched together
split_objects() { printf '%s\n' "${1//'},{'/$'\n'}"; }

has_report_status() { # REPORT_ID STATUS DESCRIPTION (searches BODY)
  if split_objects "$BODY" | grep "\"id\":$1," | grep -q "\"status\":\"$2\""; then pass "$3"; else fail "$3"; fi
}

login() { # USER PASS -> prints the access token
  request POST /auth/login "" "{\"username\":\"$(json_escape "$1")\",\"password\":\"$(json_escape "$2")\"}"
  [ "$STATUS" = 200 ] && json_string accessToken "$BODY"
}

# If the script takes a machine out of service, always put it back, even when a check fails part-way
MARKED_OUT_OF_ORDER=0
cleanup() {
  local exit_code=$?
  if [ "$MARKED_OUT_OF_ORDER" = 1 ] && [ -n "${ADMIN_TOKEN:-}" ]; then
    request PUT "/admin/equipment-issues/equipment/$EQUIPMENT_ID/out-of-order" "$ADMIN_TOKEN" '{"outOfOrder":false}'
    echo "  (cleanup: put equipment $EQUIPMENT_ID back in service, status $STATUS)"
  fi
  exit "$exit_code"
}
trap cleanup EXIT

echo "Equipment issue reporting smoke test -> $API"

request GET /auth/test
if [ "$STATUS" != 200 ]; then
  echo "Backend not reachable at $API (got $STATUS). Start it with: mvn spring-boot:run"
  exit 1
fi

echo
echo "1. Access control"
request POST /equipment-issues "" '{"equipmentId":1,"severity":"DAMAGED","description":"No token on this request"}'
expect_status 401 "Reporting without signing in is rejected"

MEMBER_TOKEN=$(login "$MEMBER_USER" "$MEMBER_PASS")
[ -n "$MEMBER_TOKEN" ] || { echo "Member login failed for '$MEMBER_USER'"; exit 1; }
ADMIN_TOKEN=$(login "$ADMIN_USER" "$ADMIN_PASS")
[ -n "$ADMIN_TOKEN" ] || { echo "Admin login failed for '$ADMIN_USER'"; exit 1; }

request GET /admin/equipment-issues "$MEMBER_TOKEN"
expect_status 403 "Members can't open the admin issues list"
request GET /admin/equipment-issues "$ADMIN_TOKEN"
expect_status 200 "Admins can open the admin issues list"

echo
echo "2. Member reports a problem"
EQUIPMENT_ID="${EQUIPMENT_ID:-}"
if [ -z "$EQUIPMENT_ID" ]; then
  request GET /machines
  EQUIPMENT_ID=$(json_number id "$BODY")
fi
[ -n "$EQUIPMENT_ID" ] || { echo "No machines found. Create one in the admin dashboard or set EQUIPMENT_ID."; exit 1; }
echo "  (using equipment id $EQUIPMENT_ID)"

# Make the script re-runnable: resolve any report this member still has open on the machine
request GET /equipment-issues/me "$MEMBER_TOKEN"
for stale_id in $(split_objects "$BODY" | grep "\"equipmentId\":$EQUIPMENT_ID," | grep -v '"status":"RESOLVED"' \
                  | grep -o '"id":[0-9]*' | cut -d: -f2); do
  request PUT "/admin/equipment-issues/$stale_id/status" "$ADMIN_TOKEN" '{"status":"RESOLVED"}'
  echo "  (resolved leftover report #$stale_id from an earlier run)"
done

REPORT_JSON="{\"equipmentId\":$EQUIPMENT_ID,\"severity\":\"OUT_OF_ORDER\",\"description\":\"Smoke test $(date +%H:%M:%S): the weight stack does not move\"}"
request POST /equipment-issues "$MEMBER_TOKEN" "$REPORT_JSON"
expect_status 201 "Member reports the machine as not working"
REPORT_ID=$(json_number id "$BODY")
if [ -z "$REPORT_ID" ]; then
  echo "  No report id in the response; stopping."
  summary
  exit 1
fi
if grep -q '"status":"REPORTED"' <<< "$BODY"; then pass "New report starts as REPORTED"; else fail "New report starts as REPORTED"; fi

request POST /equipment-issues "$MEMBER_TOKEN" "$REPORT_JSON"
expect_status 409 "A second open report on the same machine is rejected"

request POST /equipment-issues "$MEMBER_TOKEN" "{\"equipmentId\":$EQUIPMENT_ID,\"severity\":\"DAMAGED\",\"description\":\"short\"}"
expect_status 400 "A description under 10 characters is rejected"

request GET /equipment-issues/me "$MEMBER_TOKEN"
expect_status 200 "Member can list their reports"
has_report_status "$REPORT_ID" REPORTED "My Reports shows the new report as REPORTED"

request GET "/equipment-issues/equipment/$EQUIPMENT_ID" "$MEMBER_TOKEN"
expect_status 200 "Machine page issue summary loads"
if [ "$(json_number openReportCount "$BODY")" -ge 1 ] 2>/dev/null; then
  pass "Machine page summary counts the open report"
else
  fail "Machine page summary counts the open report"
fi
if grep -q 'reporterUsername' <<< "$BODY"; then
  fail "Machine page summary must not reveal who reported it"
else
  pass "Machine page summary doesn't reveal who reported it"
fi

echo
echo "3. Admin works the report"
request GET /admin/equipment-issues "$ADMIN_TOKEN"
if grep -q "\"id\":$REPORT_ID," <<< "$BODY"; then
  pass "The report appears on the admin Equipment Issues list"
else
  fail "The report appears on the admin Equipment Issues list"
fi

for status in ACKNOWLEDGED IN_PROGRESS; do
  request PUT "/admin/equipment-issues/$REPORT_ID/status" "$ADMIN_TOKEN" "{\"status\":\"$status\"}"
  expect_status 200 "Admin marks the report $status"
  request GET /equipment-issues/me "$MEMBER_TOKEN"
  has_report_status "$REPORT_ID" "$status" "Member now sees $status"
done

request PUT "/admin/equipment-issues/$REPORT_ID/status" "$ADMIN_TOKEN" '{"status":"FIXED"}'
expect_status 400 "An unknown status is rejected"

request GET /admin/equipment-issues/summary "$ADMIN_TOKEN"
expect_status 200 "Admin summary counts load"

echo
echo "4. Out of order"
MARKED_OUT_OF_ORDER=1 # from here on, cleanup puts the machine back in service on exit
request PUT "/admin/equipment-issues/equipment/$EQUIPMENT_ID/out-of-order" "$MEMBER_TOKEN" '{"outOfOrder":true}'
expect_status 403 "Members can't use the admin out-of-order switch"
request PUT "/machines/$EQUIPMENT_ID/status" "" '{"status":"Out of Order"}'
expect_status 403 "Members can't mark a machine out of order through check-in"

request PUT "/admin/equipment-issues/equipment/$EQUIPMENT_ID/out-of-order" "$ADMIN_TOKEN" '{"outOfOrder":true}'
expect_status 200 "Admin marks the machine out of order"
request GET "/machines/$EQUIPMENT_ID"
if grep -q '"status":"Out of Order"' <<< "$BODY"; then
  pass "Members see the machine as Out of Order"
else
  fail "Members see the machine as Out of Order"
fi
request PUT "/machines/$EQUIPMENT_ID/status" "$MEMBER_TOKEN" '{"status":"In Use"}'
expect_status 409 "Checking in to an out-of-order machine is refused"
request GET /admin/equipment-issues/summary "$ADMIN_TOKEN"
if [ "$(json_number outOfOrderMachines "$BODY")" -ge 1 ] 2>/dev/null; then
  pass "Admin summary counts the out-of-order machine"
else
  fail "Admin summary counts the out-of-order machine"
fi

echo
echo "5. Resolve (puts the machine back in service and cleans up)"
request PUT "/admin/equipment-issues/$REPORT_ID/status" "$ADMIN_TOKEN" '{"status":"RESOLVED"}'
expect_status 200 "Admin resolves the report"
if grep -q '"resolvedAt":"' <<< "$BODY"; then pass "Resolved report records when it was fixed"; else fail "Resolved report records when it was fixed"; fi
request GET /equipment-issues/me "$MEMBER_TOKEN"
has_report_status "$REPORT_ID" RESOLVED "Member sees it as RESOLVED (shown as Fixed in the app)"

request GET "/equipment-issues/equipment/$EQUIPMENT_ID" "$MEMBER_TOKEN"
OTHER_OPEN=$(json_number openReportCount "$BODY")
request GET "/machines/$EQUIPMENT_ID"
if [ "${OTHER_OPEN:-0}" -gt 0 ]; then
  echo "  (the machine still has $OTHER_OPEN other open report(s), so it stays out of order until cleanup)"
elif grep -q '"status":"Available"' <<< "$BODY"; then
  pass "Resolving the last open report put the machine back in service"
  MARKED_OUT_OF_ORDER=0
else
  fail "Resolving the last open report put the machine back in service"
fi

summary
