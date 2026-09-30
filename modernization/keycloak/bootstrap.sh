#!/usr/bin/env bash
#
# Second half of the Keycloak configuration: everything that carries a secret and therefore cannot
# live in keycloak/realm-carddemo.json.
#
#   * the confidential client secrets (carddemo-identity-admin, carddemo-authorization-svc,
#     carddemo-transaction-svc),
#   * the realm-management roles the identity service needs to provision users,
#   * the SERVICE realm role of the authorization and transaction service accounts,
#   * the TOTP credential of the test users the E2E suite signs in as, and the one user it enrols.
#
# The realm import is idempotent (Keycloak skips an existing realm) and so is this script: it
# overwrites the secrets and role mappings, and recreates the E2E users from scratch.
#
# Runs inside the Keycloak image (it only needs bash and kcadm.sh) and against a Keycloak that is
# already up; docker-compose.yml wires it as the keycloak-bootstrap one-shot service.
set -euo pipefail

KCADM=${KCADM:-/opt/keycloak/bin/kcadm.sh}
KEYCLOAK_URL=${KEYCLOAK_URL:?KEYCLOAK_URL must be set, e.g. http://keycloak:8080}
REALM=${KEYCLOAK_REALM:-carddemo}
ADMIN_USER=${KEYCLOAK_ADMIN:?KEYCLOAK_ADMIN must be set}
ADMIN_PASSWORD=${KEYCLOAK_ADMIN_PASSWORD:?KEYCLOAK_ADMIN_PASSWORD must be set}
: "${KEYCLOAK_IDENTITY_CLIENT_SECRET:?KEYCLOAK_IDENTITY_CLIENT_SECRET must be set}"
: "${KEYCLOAK_AUTHORIZATION_CLIENT_SECRET:?KEYCLOAK_AUTHORIZATION_CLIENT_SECRET must be set}"
: "${KEYCLOAK_TRANSACTION_CLIENT_SECRET:?KEYCLOAK_TRANSACTION_CLIENT_SECRET must be set}"
: "${E2E_ADMIN_TOTP_SECRET:?E2E_ADMIN_TOTP_SECRET must be set}"
: "${E2E_USER_TOTP_SECRET:?E2E_USER_TOTP_SECRET must be set}"
: "${E2E_LOCKOUT_TOTP_SECRET:?E2E_LOCKOUT_TOTP_SECRET must be set}"
: "${E2E_PASSWORD:?E2E_PASSWORD must be set}"

log() { printf '[keycloak-bootstrap] %s\n' "$*"; }

wait_for_keycloak() {
  local host port attempt
  host=${KEYCLOAK_URL#*://}
  host=${host%%/*}
  port=${host##*:}
  host=${host%%:*}
  [ "$port" = "$host" ] && port=8080
  for attempt in $(seq 1 120); do
    if (exec 3<>"/dev/tcp/${host}/${port}") 2>/dev/null; then
      exec 3<&- || true
      log "Keycloak is accepting connections on ${host}:${port}"
      return 0
    fi
    sleep 2
  done
  log "Keycloak did not open ${host}:${port} in time"
  return 1
}

login() {
  local attempt
  for attempt in $(seq 1 60); do
    if "$KCADM" config credentials \
      --server "$KEYCLOAK_URL" \
      --realm master \
      --user "$ADMIN_USER" \
      --password "$ADMIN_PASSWORD" >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done
  log "could not authenticate against the master realm"
  return 1
}

client_uuid() {
  "$KCADM" get clients -r "$REALM" -q "clientId=$1" --fields id --format csv --noquotes | head -n 1
}

set_client_secret() {
  local client_id=$1 secret=$2 uuid
  uuid=$(client_uuid "$client_id")
  if [ -z "$uuid" ]; then
    log "client $client_id is missing from realm $REALM - was realm-carddemo.json imported?"
    return 1
  fi
  "$KCADM" update "clients/$uuid" -r "$REALM" -s "secret=$secret" >/dev/null
  log "secret set on $client_id"
}

grant_realm_management() {
  local service_account=$1
  shift
  "$KCADM" add-roles -r "$REALM" \
    --uusername "$service_account" \
    --cclientid realm-management \
    "$@" >/dev/null
  log "realm-management roles granted to $service_account"
}

# The E2E users are the only ones whose TOTP secret is known, so they are the only ones that can be
# driven headlessly. Everyone else enrols interactively through CONFIGURE_TOTP, which is what the last
# user seeded here is for: it is created with a password only, as the identity service creates one.
seed_e2e_user() {
  local username=$1 first=$2 last=$3 user_type=$4 realm_role=$5 totp_secret=${6:-} existing created otp=

  existing=$("$KCADM" get users -r "$REALM" -q "username=$username" -q exact=true \
    --fields id --format csv --noquotes | head -n 1)
  if [ -n "$existing" ]; then
    "$KCADM" delete "users/$existing" -r "$REALM"
  fi

  if [ -n "$totp_secret" ]; then
    otp=$(cat <<JSON
,
    {
      "type": "otp",
      "userLabel": "carddemo-e2e",
      "secretData": "{\"value\":\"$totp_secret\"}",
      "credentialData": "{\"subType\":\"totp\",\"digits\":6,\"counter\":0,\"period\":30,\"algorithm\":\"HmacSHA1\"}"
    }
JSON
)
  fi

  "$KCADM" create users -r "$REALM" -f - >/dev/null <<JSON
{
  "username": "$username",
  "enabled": true,
  "emailVerified": true,
  "email": "$username@carddemo.local",
  "firstName": "$first",
  "lastName": "$last",
  "attributes": { "userType": ["$user_type"] },
  "requiredActions": [],
  "credentials": [
    { "type": "password", "value": "$E2E_PASSWORD", "temporary": false }$otp
  ]
}
JSON

  "$KCADM" add-roles -r "$REALM" --uusername "$username" --rolename "$realm_role" >/dev/null

  # CONFIGURE_TOTP is a default required action of the realm, so it lands on every user created here even
  # though the payload asked for none of it. It is left on the user that has no authenticator yet and
  # taken off the ones that were given one.
  created=$("$KCADM" get users -r "$REALM" -q "username=$username" -q exact=true \
    --fields id --format csv --noquotes | head -n 1)
  if [ -n "$totp_secret" ]; then
    "$KCADM" update "users/$created" -r "$REALM" -s 'requiredActions=[]' >/dev/null
    log "$username seeded with a password and a pre-enrolled TOTP credential"
  else
    "$KCADM" update "users/$created" -r "$REALM" -s 'requiredActions=["CONFIGURE_TOTP"]' >/dev/null
    log "$username seeded with a password only and has to enrol an authenticator"
  fi
}

wait_for_keycloak
login

set_client_secret carddemo-identity-admin "$KEYCLOAK_IDENTITY_CLIENT_SECRET"
set_client_secret carddemo-authorization-svc "$KEYCLOAK_AUTHORIZATION_CLIENT_SECRET"
set_client_secret carddemo-transaction-svc "$KEYCLOAK_TRANSACTION_CLIENT_SECRET"

# view-realm is what reading a realm role by name needs, and assigning ADMIN or USER to a user it has
# just created begins with reading the role: manage-users alone leaves provisioning refused half way.
grant_realm_management service-account-carddemo-identity-admin \
  --rolename manage-users \
  --rolename view-users \
  --rolename query-users \
  --rolename view-realm
"$KCADM" add-roles -r "$REALM" \
  --uusername service-account-carddemo-authorization-svc \
  --rolename SERVICE >/dev/null
log "SERVICE role granted to service-account-carddemo-authorization-svc"
"$KCADM" add-roles -r "$REALM" \
  --uusername service-account-carddemo-transaction-svc \
  --rolename SERVICE >/dev/null
log "SERVICE role granted to service-account-carddemo-transaction-svc"

seed_e2e_user admin001 MARGARET GOLD A ADMIN "$E2E_ADMIN_TOTP_SECRET"
seed_e2e_user user0001 LAWRENCE THOMAS U USER "$E2E_USER_TOTP_SECRET"

# The third user exists to be refused: the realm counts failed attempts per user and locks the account
# for a while, so the tests that key a wrong code drive this one and leave the other two able to sign on.
seed_e2e_user user0002 DIANE HOLT U USER "$E2E_LOCKOUT_TOTP_SECRET"

# The fourth user has a password and no authenticator, as a user the identity service has just created
# has: the realm asks it to enrol one before it lets the user through, which is what COSGN00C had no
# equivalent of and what the enrolment test drives.
seed_e2e_user user0003 JOHN CARTER U USER

log "realm $REALM is ready"
