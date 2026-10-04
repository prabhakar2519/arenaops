import type { ArenaRuntimeConfig } from '../../environments/runtime-config';

const STATE_KEY = 'arenaops.oauth.state';
const VERIFIER_KEY = 'arenaops.oauth.verifier';

function base64Url(bytes: Uint8Array): string {
  return btoa(String.fromCharCode(...bytes)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

export async function authorizationUrl(config: ArenaRuntimeConfig, origin: string, storage: Storage,
    cryptoProvider: Crypto = crypto, resetPassword = false): Promise<string> {
  const verifier = base64Url(cryptoProvider.getRandomValues(new Uint8Array(32)));
  const state = base64Url(cryptoProvider.getRandomValues(new Uint8Array(32)));
  const digest = await cryptoProvider.subtle.digest('SHA-256', new TextEncoder().encode(verifier));
  storage.setItem(STATE_KEY, state);
  storage.setItem(VERIFIER_KEY, verifier);
  const endpoint = resetPassword ? 'login-actions/reset-credentials' : 'protocol/openid-connect/auth';
  const url = new URL(`${config.keycloakBaseUrl}/realms/${config.keycloakRealm}/${endpoint}`);
  url.search = new URLSearchParams({ client_id: 'arena-ui', redirect_uri: `${origin}/login/callback`,
    response_type: 'code', scope: 'openid profile email roles', state,
    code_challenge: base64Url(new Uint8Array(digest)), code_challenge_method: 'S256' }).toString();
  return url.toString();
}

export function consumeVerifier(state: string | undefined, storage: Storage): string {
  const expected = storage.getItem(STATE_KEY);
  const verifier = storage.getItem(VERIFIER_KEY);
  // Authorization codes and their verifier are single-use, even after a failed exchange.
  storage.removeItem(STATE_KEY);
  storage.removeItem(VERIFIER_KEY);
  if (!state || state !== expected || !verifier) {
    throw new Error('Sign-in verification failed. Please start sign-in again.');
  }
  return verifier;
}
