#!/usr/bin/env bash
# Smoke test for "call staff" help requests against a RUNNING backend.
#
# Walks the real member -> staff -> member loop over HTTP and prints PASS/FAIL for each check:
# calling staff, the one-open-request rule, On the way / Too busy, Done helping, the member's
# "Received help" and cancelling. It writes real data (a few closed help requests plus
# activity-log entries), so point it at a local or dev database, never production.
#
# Usage (Git Bash, macOS or Linux), from the repo root:
#   MEMBER_USER=urecuser MEMBER_PASS='secret' ADMIN_USER=urecadmin ADMIN_PASS='secret' \
#     ./scripts/smoke-test-help-requests.sh
#
# Optional:
#   API=http://localhost:8080/api   backend base URL (default shown)
#   EQUIPMENT_ID=3                  machine to call staff to (default: first machine from GET /machines)

set -u

API="${API:-http://localhost:8080/api}"
: "${MEMBER_USER:?Set MEMBER_USER to a regular member account}"
: "${MEMBER_PASS:?Set MEMBER_PASS}"
: "${ADMIN_USER:?Set ADMIN_USER to an account with the ADMIN or ROLE_ADMIN role}"
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

expect_field() { # NAME EXPECTED DESCRIPTION (string field in BODY)
  local actual
  actual=$(json_string "$1" "$BODY")
  if [ "$actual" = "$2" ]; then pass "$3"; else fail "$3 (expected $1=$2, got '${actual}')"; fi
}

json_escape() { sed 's/\\/\\\\/g; s/"/\\"/g' <<< "$1"; }
json_string() { sed -n "s/.*\"$1\":\"\([^\"]*\)\".*/\1/p" <<< "$2" | head -1; }
json_number() { grep -o "\"$1\":[0-9]*" <<< "$2" | head -1 | cut -d: -f2; }

# One JSON object per line, so a request's fields can be matched together
split_objects() { printf '%s\n' "${1//'},{'/$'\n'}"; }

login() { # USER PASS -> prints the access token
  request POST /auth/login "" "{\"username\":\"$(json_escape "$1")\",\"password\":\"$(json_escape "$2")\"}"
  [ "$STATUS" = 200 ] && json_string accessToken "$BODY"
}

call_staff() { # -> sets REQUEST_ID (empty on failure)
  request POST /help-requests "$MEMBER_TOKEN" "{\"equipmentId\":$EQUIPMENT_ID}"
  REQUEST_ID=''
  [ "$STATUS" = 201 ] && REQUEST_ID=$(json_number id "$BODY")
}

# Never leave the member with an open request, even when a check fails part-way
cleanup() {
  local exit_code=$?
  if [ -n "${MEMBER_TOKEN:-}" ]; then
    request GET /help-requests/me/active "$MEMBER_TOKEN"
    if [ "$STATUS" = 200 ]; then
      local open_id
      open_id=$(json_number id "$BODY")
      request POST "/help-requests/$open_id/cancel" "$MEMBER_TOKEN"
      echo "  (cleanup: cancelled open help request $open_id, status $STATUS)"
    fi
  fi
  exit "$exit_code"
}
trap cleanup EXIT

echo "Help request smoke test -> $API"

request GET /auth/test
if [ "$STATUS" != 200 ]; then
  echo "Backend not reachable at $API (got $STATUS). Start it with: mvn spring-boot:run"
  exit 1
fi

echo
echo "1. Access control"
request POST /help-requests "" '{"equipmentId":1}'
expect_status 401 "Calling staff without signing in is rejected"
request GET /admin/help-requests
expect_status 401 "The staff queue needs signing in"

MEMBER_TOKEN=$(login "$MEMBER_USER" "$MEMBER_PASS")
[ -n "$MEMBER_TOKEN" ] || { echo "Member login failed for '$MEMBER_USER'"; exit 1; }
ADMIN_TOKEN=$(login "$ADMIN_USER" "$ADMIN_PASS")
[ -n "$ADMIN_TOKEN" ] || { echo "Admin login failed for '$ADMIN_USER'"; exit 1; }

request GET /admin/help-requests "$MEMBER_TOKEN"
expect_status 403 "Members can't open the staff queue"
request GET /admin/help-requests "$ADMIN_TOKEN"
expect_status 200 "Staff can open the queue"

EQUIPMENT_ID="${EQUIPMENT_ID:-}"
if [ -z "$EQUIPMENT_ID" ]; then
  request GET /machines
  EQUIPMENT_ID=$(json_number id "$BODY")
fi
[ -n "$EQUIPMENT_ID" ] || { echo "No machines found; set EQUIPMENT_ID"; exit 1; }

# Re-runnable: close anything a previous (interrupted) run left open
request GET /help-requests/me/active "$MEMBER_TOKEN"
if [ "$STATUS" = 200 ]; then
  request POST "/help-requests/$(json_number id "$BODY")/cancel" "$MEMBER_TOKEN"
  echo "  (closed a help request left open by an earlier run)"
fi

echo
echo "2. Member calls staff to machine $EQUIPMENT_ID"
request GET /help-requests/me/active "$MEMBER_TOKEN"
expect_status 204 "No open request to start with"
call_staff
expect_status 201 "Calling staff creates a request"
[ -n "$REQUEST_ID" ] || { echo "Couldn't create a help request; stopping"; summary; exit 1; }
expect_field status REQUEST_RECEIVED "It starts as Request received"
DEMO_VIDEO=$(json_string videoUrl "$BODY")
DEMO_GIF=$(json_string gifUrl "$BODY")
if [[ "$DEMO_VIDEO" == http* ]] && [[ "$DEMO_GIF" == http* ]]; then
  pass "The response includes demo video and GIF links"
else
  fail "The response includes demo video and GIF links (video '$DEMO_VIDEO', GIF '$DEMO_GIF')"
fi
# The placeholder media live on third-party hosts, so check they still load
for media in "video $DEMO_VIDEO" "image $DEMO_GIF"; do
  kind="${media%% *}" url="${media#* }"
  [[ "$url" == http* ]] || continue
  loaded=$(curl -sL -o /dev/null -r 0-1023 --max-time 20 -w '%{http_code} %{content_type}' "$url")
  if [[ "$loaded" =~ ^20[06]\ $kind/ ]]; then
    pass "The demo $kind loads ($loaded)"
  else
    fail "The demo $kind loads ($url answered '$loaded')"
  fi
done
request POST /help-requests "$MEMBER_TOKEN" "{\"equipmentId\":$EQUIPMENT_ID}"
expect_status 409 "A second open request is rejected"
request GET /help-requests/me/active "$MEMBER_TOKEN"
if [ "$STATUS" = 200 ] && [ "$(json_number id "$BODY")" = "$REQUEST_ID" ]; then
  pass "The member's active request is the new one"
else
  fail "The member's active request is the new one (status $STATUS)"
fi

echo
echo "3. Staff respond and finish"
request GET /admin/help-requests "$ADMIN_TOKEN"
if split_objects "$BODY" | grep "\"id\":$REQUEST_ID," | grep -q "\"memberUsername\":\"$MEMBER_USER\""; then
  pass "The request is in the staff queue with the member's username"
else
  fail "The request is in the staff queue with the member's username"
fi
request PUT "/admin/help-requests/$REQUEST_ID/status" "$ADMIN_TOKEN" '{"status":"TOO_BUSY"}'
expect_status 200 "Staff mark it Too busy"
request GET "/help-requests/$REQUEST_ID" "$MEMBER_TOKEN"
expect_field status TOO_BUSY "The member sees Too busy"
request PUT "/admin/help-requests/$REQUEST_ID/status" "$ADMIN_TOKEN" '{"status":"ON_THE_WAY"}'
expect_status 200 "Staff switch it to On the way"
request GET "/help-requests/$REQUEST_ID" "$MEMBER_TOKEN"
expect_field status ON_THE_WAY "The member sees On the way"
if grep -q '"staffUsername"' <<< "$BODY"; then
  fail "The member's view doesn't name the staff account"
else
  pass "The member's view doesn't name the staff account"
fi
request PUT "/admin/help-requests/$REQUEST_ID/status" "$ADMIN_TOKEN" '{"status":"RESOLVED"}'
expect_status 400 "Staff can't set RESOLVED directly (they use Done helping)"
request POST "/admin/help-requests/$REQUEST_ID/done" "$ADMIN_TOKEN"
expect_status 200 "Staff click Done helping"
request GET "/help-requests/$REQUEST_ID" "$MEMBER_TOKEN"
expect_field status RESOLVED "The member sees it resolved"
expect_field closedBy STAFF "...closed by staff"
request POST "/help-requests/$REQUEST_ID/received" "$MEMBER_TOKEN"
expect_status 409 "A closed request can't be closed again"

echo
echo "4. Member confirms they received help"
call_staff
expect_status 201 "The member can call staff again"
request PUT "/admin/help-requests/$REQUEST_ID/status" "$ADMIN_TOKEN" '{"status":"ON_THE_WAY"}'
expect_status 200 "Staff mark it On the way"
request POST "/help-requests/$REQUEST_ID/received" "$MEMBER_TOKEN"
expect_status 200 "The member clicks Received help"
expect_field closedBy MEMBER "...closed by the member"
request POST "/admin/help-requests/$REQUEST_ID/done" "$ADMIN_TOKEN"
expect_status 409 "Done helping on an already-closed request is rejected"
request GET "/admin/help-requests/history?limit=5" "$ADMIN_TOKEN"
if split_objects "$BODY" | grep "\"id\":$REQUEST_ID," | grep -q '"closedBy":"MEMBER"'; then
  pass "Staff see it in Recently closed"
else
  fail "Staff see it in Recently closed"
fi

echo
echo "5. Member cancels"
call_staff
expect_status 201 "The member calls staff once more"
request POST "/help-requests/$REQUEST_ID/cancel" "$MEMBER_TOKEN"
expect_status 200 "The member cancels"
expect_field status CANCELLED "...and it's cancelled"
request GET /help-requests/me/active "$MEMBER_TOKEN"
expect_status 204 "Nothing is left open"

summary
