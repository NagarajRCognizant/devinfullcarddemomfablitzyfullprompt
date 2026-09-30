#!/usr/bin/env bash
#
# Adds the address a remote browser uses to the redirect URIs, the web origins and the sign-off
# redirect URIs of carddemo-ui.
#
# The realm import lists the loopback addresses only, which is all a browser on the Docker host needs.
# A browser somewhere else reaches the SPA on another address, and the realm refuses a redirect it was
# not told about, so that address has to be added before the sign-on can come back and before signing
# off can return to the page.
#
#   CARDDEMO_PUBLIC_UI_URL  the address the browser opens the SPA on, no trailing slash
#   KEYCLOAK_URL            the realm as this machine reaches it, default http://127.0.0.1:8088/auth
#
# Usage, from modernization/ with the stack up and .env loaded:
#   CARDDEMO_PUBLIC_UI_URL=https://ui.example.test ./keycloak/public-redirects.sh
#
# The whole client is read, amended and written back rather than patched field by field: a write of a
# single attribute replaces the map, which would drop the PKCE method and the token lifespan the import
# set.
set -euo pipefail

KEYCLOAK_URL=${KEYCLOAK_URL:-http://127.0.0.1:8088/auth}
REALM=${KEYCLOAK_REALM:-carddemo}
ADMIN_USER=${KEYCLOAK_ADMIN:?KEYCLOAK_ADMIN must be set}
ADMIN_PASSWORD=${KEYCLOAK_ADMIN_PASSWORD:?KEYCLOAK_ADMIN_PASSWORD must be set}
UI_URL=${CARDDEMO_PUBLIC_UI_URL:?CARDDEMO_PUBLIC_UI_URL must be set}
CLIENT_ID=${KEYCLOAK_BROWSER_CLIENT_ID:-carddemo-ui}

KEYCLOAK_URL=${KEYCLOAK_URL%/}
UI_URL=${UI_URL%/}

token=$(curl -sf --data-urlencode "username=$ADMIN_USER" --data-urlencode "password=$ADMIN_PASSWORD" \
  -d 'grant_type=password' -d 'client_id=admin-cli' \
  "$KEYCLOAK_URL/realms/master/protocol/openid-connect/token" |
  python3 -c 'import json,sys; print(json.load(sys.stdin)["access_token"])')

client=$(curl -sf -H "Authorization: Bearer $token" \
  "$KEYCLOAK_URL/admin/realms/$REALM/clients?clientId=$CLIENT_ID")

amended=$(CLIENT="$client" UI_URL="$UI_URL" python3 - <<'PY'
import json, os, sys

found = json.loads(os.environ['CLIENT'])
if not found:
    sys.exit('the realm has no such client')
client = found[0]
ui = os.environ['UI_URL']


def added(values, value):
    return values if value in values else [*values, value]


client['redirectUris'] = added(client.get('redirectUris', []), ui + '/*')
client['webOrigins'] = added(client.get('webOrigins', []), ui)
attributes = client.setdefault('attributes', {})
signOff = attributes.get('post.logout.redirect.uris', '')
entries = [entry for entry in signOff.split('##') if entry]
attributes['post.logout.redirect.uris'] = '##'.join(added(entries, ui + '/*'))
print(json.dumps({'id': client['id'], 'client': client}))
PY
)

id=$(python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<<"$amended")
python3 -c 'import json,sys; print(json.dumps(json.load(sys.stdin)["client"]))' <<<"$amended" |
  curl -sf -X PUT -H "Authorization: Bearer $token" -H 'Content-Type: application/json' \
    --data-binary @- "$KEYCLOAK_URL/admin/realms/$REALM/clients/$id"

printf '[public-redirects] %s may now redirect to %s\n' "$CLIENT_ID" "$UI_URL"
