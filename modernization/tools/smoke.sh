#!/usr/bin/env bash
# Local API smoke test for the converted sign-on, user administration and account endpoints.
#
# Runs against the gateway, which is the address the UI uses.
# Usage: modernization/tools/smoke.sh [base-url]   (default http://127.0.0.1:8090)
#
# Requires the services to run with CARDDEMO_SECURITY_MODE=legacy: it signs on with POST /api/signon,
# which the default keycloak mode does not map, and no shell can obtain a browser token of the realm
# because carddemo-ui has the direct access grant disabled and requires a second factor. This is the
# COSGN00C parity evidence; the default mode is exercised end to end by the Playwright suite in e2e/.
set -uo pipefail
BASE="${1:-http://127.0.0.1:8090}"

call() {
  local label="$1"; shift
  echo "### ${label}"
  echo "\$ curl $*"
  curl -sS -o /tmp/smoke-body -w 'HTTP %{http_code}\n' "$@"
  python3 -m json.tool < /tmp/smoke-body 2>/dev/null || cat /tmp/smoke-body
  echo
}

signon() {
  curl -sS -X POST -H 'Content-Type: application/json' \
    -d "{\"userId\":\"$1\",\"password\":\"$2\"}" "${BASE}/api/signon"
}

token_of() {
  python3 -c 'import json,sys; print(json.load(sys.stdin).get("accessToken",""))'
}

call "health" "${BASE}/actuator/health"

# COSGN00C: the required-field checks, the two rejection paths and the two XCTL targets.
call "sign-on - user id required" -X POST -H 'Content-Type: application/json' \
  -d '{"userId":"","password":"PASSWORD"}' "${BASE}/api/signon"
call "sign-on - password required" -X POST -H 'Content-Type: application/json' \
  -d '{"userId":"ADMIN001","password":""}' "${BASE}/api/signon"
call "sign-on - unknown user" -X POST -H 'Content-Type: application/json' \
  -d '{"userId":"NOSUCH","password":"PASSWORD"}' "${BASE}/api/signon"
call "sign-on - wrong password" -X POST -H 'Content-Type: application/json' \
  -d '{"userId":"ADMIN001","password":"WRONG"}' "${BASE}/api/signon"
call "sign-on - administrator, lower case is folded up" -X POST -H 'Content-Type: application/json' \
  -d '{"userId":"admin001","password":"password"}' "${BASE}/api/signon"
call "sign-on - regular user" -X POST -H 'Content-Type: application/json' \
  -d '{"userId":"USER0001","password":"PASSWORD"}' "${BASE}/api/signon"

ADMIN_TOKEN=$(signon ADMIN001 PASSWORD | token_of)
USER_TOKEN=$(signon USER0001 PASSWORD | token_of)
ADMIN_AUTH=(-H "Authorization: Bearer ${ADMIN_TOKEN}")
USER_AUTH=(-H "Authorization: Bearer ${USER_TOKEN}")

call "account view without a token is refused" "${BASE}/api/accounts/00000000001"

# COADM01C: the option table and the three rejections of PROCESS-ENTER-KEY.
call "admin menu (COADM01C)" "${ADMIN_AUTH[@]}" "${BASE}/api/admin-menu"
call "admin menu - option 1 starts COUSR00C" "${ADMIN_AUTH[@]}" \
  "${BASE}/api/admin-menu/selection?option=1"
call "admin menu - option 9 is out of range" "${ADMIN_AUTH[@]}" \
  "${BASE}/api/admin-menu/selection?option=9"
call "admin menu - a regular user is refused" "${USER_AUTH[@]}" "${BASE}/api/admin-menu"

# COUSR00C: ten rows a page, keyed paging in both directions.
call "user list - first page" "${ADMIN_AUTH[@]}" "${BASE}/api/users?direction=FIRST"
call "user list - keyed start" "${ADMIN_AUTH[@]}" "${BASE}/api/users?userId=USER0001&direction=FIRST"
call "user list - page forward past the last user" "${ADMIN_AUTH[@]}" \
  "${BASE}/api/users?userId=USER0005&direction=NEXT"
call "user list - page back from the first user" "${ADMIN_AUTH[@]}" \
  "${BASE}/api/users?userId=ADMIN001&direction=PREVIOUS"

# COUSR01C / COUSR02C / COUSR03C: the maintenance lifecycle of one record.
call "user add - missing first name" -X POST "${ADMIN_AUTH[@]}" -H 'Content-Type: application/json' \
  -d '{"firstName":"","lastName":"SMOKE","userId":"SMOKE001","password":"PASSWORD","userType":"U"}' \
  "${BASE}/api/users"
call "user add" -X POST "${ADMIN_AUTH[@]}" -H 'Content-Type: application/json' \
  -d '{"firstName":"SMOKE","lastName":"TESTER","userId":"SMOKE001","password":"PASSWORD","userType":"U"}' \
  "${BASE}/api/users"
call "user add - duplicate user id" -X POST "${ADMIN_AUTH[@]}" -H 'Content-Type: application/json' \
  -d '{"firstName":"SMOKE","lastName":"TESTER","userId":"SMOKE001","password":"PASSWORD","userType":"U"}' \
  "${BASE}/api/users"
call "user fetch" "${ADMIN_AUTH[@]}" "${BASE}/api/users/SMOKE001"
call "user fetch - unknown user" "${ADMIN_AUTH[@]}" "${BASE}/api/users/NOSUCH"
call "user update - nothing modified" -X PUT "${ADMIN_AUTH[@]}" -H 'Content-Type: application/json' \
  -d '{"firstName":"SMOKE","lastName":"TESTER","password":"PASSWORD","userType":"U"}' \
  "${BASE}/api/users/SMOKE001"
call "user update" -X PUT "${ADMIN_AUTH[@]}" -H 'Content-Type: application/json' \
  -d '{"firstName":"SMOKED","lastName":"TESTER","password":"PASSWORD","userType":"A"}' \
  "${BASE}/api/users/SMOKE001"
call "user delete" -X DELETE "${ADMIN_AUTH[@]}" "${BASE}/api/users/SMOKE001"
call "user delete - already gone" -X DELETE "${ADMIN_AUTH[@]}" "${BASE}/api/users/SMOKE001"

# COMEN01C access rule and the two account screens.
call "menu options for a regular user (COMEN01C)" "${USER_AUTH[@]}" "${BASE}/api/menu"
call "menu options for an administrator" "${ADMIN_AUTH[@]}" "${BASE}/api/menu"
call "account view prompt (empty COACTVW map)" "${USER_AUTH[@]}" "${BASE}/api/accounts/view"
call "account view - existing account" "${USER_AUTH[@]}" "${BASE}/api/accounts/00000000001"
call "account view - failing account filter edit" "${USER_AUTH[@]}" "${BASE}/api/accounts/0000000000A"
call "account view - unknown account" "${USER_AUTH[@]}" "${BASE}/api/accounts/99999999999"
call "account update - fetch for update" "${USER_AUTH[@]}" "${BASE}/api/accounts/00000000009/update"

ORIGINAL=$(curl -sS "${USER_AUTH[@]}" "${BASE}/api/accounts/00000000009/update" \
  | python3 -c 'import json,sys; print(json.dumps(json.load(sys.stdin)["original"]))')
CHANGED=$(python3 - "$ORIGINAL" <<'PY'
import json, sys
form = json.loads(sys.argv[1])
form.update({"ssnPart1": "123", "ssnPart2": "45", "ssnPart3": "6789", "ficoScore": "700",
             "state": "NC", "zip": "27610", "phone1Area": "201", "phone2Area": "201",
             "creditLimit": "6000.00"})
print(json.dumps({"original": json.loads(sys.argv[1]), "updated": form}))
PY
)
UNCHANGED=$(python3 - "$ORIGINAL" <<'PY'
import json, sys
form = json.loads(sys.argv[1])
print(json.dumps({"original": form, "updated": form}))
PY
)

call "account update - no change detected" -X POST "${USER_AUTH[@]}" \
  -H 'Content-Type: application/json' -d "${UNCHANGED}" \
  "${BASE}/api/accounts/00000000009/update/validate"
call "account update - edits pass, waiting for F5" -X POST "${USER_AUTH[@]}" \
  -H 'Content-Type: application/json' -d "${CHANGED}" \
  "${BASE}/api/accounts/00000000009/update/validate"
call "account update - confirm (F5) rewrites account then customer" -X POST "${USER_AUTH[@]}" \
  -H 'Content-Type: application/json' -d "${CHANGED}" \
  "${BASE}/api/accounts/00000000009/update/confirm"
call "account update - stale snapshot is rejected" -X POST "${USER_AUTH[@]}" \
  -H 'Content-Type: application/json' -d "${CHANGED}" \
  "${BASE}/api/accounts/00000000009/update/confirm"
call "account view - committed values" "${USER_AUTH[@]}" "${BASE}/api/accounts/00000000009"
