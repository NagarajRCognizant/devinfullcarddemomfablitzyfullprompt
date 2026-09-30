/**
 * Where the realm is and which client this page is, read from the environment at build time.
 *
 * <p>Nothing secret is here: the SPA is a public client and authenticates with the authorization code
 * flow and PKCE, so the only values it needs are public ones. A confidential client secret must never
 * reach this bundle.
 */
export interface RealmConfig {
  authority: string;
  clientId: string;
  redirectUri: string;
  postLogoutRedirectUri: string;
}

function origin(): string {
  return typeof window === 'undefined' ? 'http://127.0.0.1:5173' : window.location.origin;
}

export function realmConfig(env: Record<string, string | undefined> = import.meta.env): RealmConfig {
  const url = env.VITE_KEYCLOAK_URL ?? 'http://127.0.0.1:8088';
  const realm = env.VITE_KEYCLOAK_REALM ?? 'carddemo';
  return {
    authority: `${url.replace(/\/$/, '')}/realms/${realm}`,
    clientId: env.VITE_KEYCLOAK_CLIENT_ID ?? 'carddemo-ui',
    redirectUri: env.VITE_KEYCLOAK_REDIRECT_URI ?? `${origin()}/callback`,
    postLogoutRedirectUri: env.VITE_KEYCLOAK_POST_LOGOUT_REDIRECT_URI ?? `${origin()}/`,
  };
}
