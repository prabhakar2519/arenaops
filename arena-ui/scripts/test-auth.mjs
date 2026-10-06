import assert from 'node:assert/strict';
import { webcrypto } from 'node:crypto';
import fs from 'node:fs';
import { test } from 'node:test';
import ts from 'typescript';

async function loadPureSource(relative) {
  const source = fs.readFileSync(new URL(relative, import.meta.url), 'utf8');
  const compiled = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.ES2022, target: ts.ScriptTarget.ES2022 }
  }).outputText;
  return import('data:text/javascript;base64,' + Buffer.from(compiled).toString('base64'));
}
const { validateRuntimeConfig } = await loadPureSource('../src/environments/runtime-config.ts');
const { authorizationUrl, consumeVerifier } = await loadPureSource('../src/app/services/oidc.ts');

function storage() {
  const values = new Map();
  return { getItem: key => values.get(key) ?? null, setItem: (key, value) => values.set(key, value),
    removeItem: key => values.delete(key) };
}

for (const [arenaEnv, keycloakRealm, origin, keycloakBaseUrl] of [
  ['dev', 'arena-dev', 'http://localhost:4200', 'http://localhost:9091/auth'],
  ['sit', 'arena-sit', 'https://sit.arenaops.in', 'https://sit.arenaops.in/auth'],
  ['prod', 'arena', 'https://arenaops.in', 'https://arenaops.in/auth']
]) {
  test(`${arenaEnv} login selects its realm, callback and PKCE challenge`, async () => {
    const config = validateRuntimeConfig({ arenaEnv, keycloakRealm, keycloakBaseUrl });
    const session = storage();
    const url = new URL(await authorizationUrl(config, origin, session, webcrypto));
    assert.equal(url.pathname, `/auth/realms/${keycloakRealm}/protocol/openid-connect/auth`);
    assert.equal(url.searchParams.get('redirect_uri'), origin + '/login/callback');
    assert.equal(url.searchParams.get('client_id'), 'arena-ui');
    assert.equal(url.searchParams.get('code_challenge_method'), 'S256');
    const state = url.searchParams.get('state');
    const verifier = consumeVerifier(state, session);
    assert.match(verifier, /^[A-Za-z0-9_-]{43}$/);
    const challenge = Buffer.from(await webcrypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier))).toString('base64url');
    assert.equal(url.searchParams.get('code_challenge'), challenge);
    assert.throws(() => consumeVerifier(state, session), /verification failed/);
  });
}

test('callbacks with missing or mismatched state are rejected and clear their verifier', async () => {
  for (const state of [undefined, 'wrong-state']) {
    const session = storage();
    const url = new URL(await authorizationUrl({ arenaEnv: 'sit', keycloakRealm: 'arena-sit',
      keycloakBaseUrl: 'https://sit.arenaops.in/auth' }, 'https://sit.arenaops.in', session, webcrypto));
    assert.throws(() => consumeVerifier(state, session), /verification failed/);
    assert.throws(() => consumeVerifier(url.searchParams.get('state'), session), /verification failed/);
  }
});

test('password reset carries the same PKCE and state protections', async () => {
  const session = storage();
  const url = new URL(await authorizationUrl({ arenaEnv: 'dev', keycloakRealm: 'arena-dev',
    keycloakBaseUrl: 'http://localhost:9091/auth' }, 'http://localhost:4200', session, webcrypto, true));
  assert.equal(url.pathname, '/auth/realms/arena-dev/login-actions/reset-credentials');
  assert.equal(url.searchParams.get('code_challenge_method'), 'S256');
  assert.ok(consumeVerifier(url.searchParams.get('state'), session));
});

test('runtime configuration fails closed on missing, inconsistent or insecure remote configuration', () => {
  assert.throws(() => validateRuntimeConfig(undefined));
  assert.throws(() => validateRuntimeConfig({ arenaEnv: 'sit', keycloakRealm: 'arena', keycloakBaseUrl: 'https://sit.arenaops.in/auth' }));
  assert.throws(() => validateRuntimeConfig({ arenaEnv: 'sit', keycloakRealm: 'arena-sit', keycloakBaseUrl: 'http://sit.arenaops.in/auth' }));
});


test('runtime config resolves from the current host root on every SPA route', () => {
  const html = fs.readFileSync(new URL('../src/index.html', import.meta.url), 'utf8');
  const script = html.match(/<script\b[^>]*\bsrc=["']([^"']*arena-config\.js)["'][^>]*>/);
  assert.ok(script, 'index.html must load runtime configuration');
  assert.equal(script[1], '/arena-config.js');
  // Resolve against the document URL, including before the base element is parsed.
  for (const origin of ['http://localhost:4200', 'https://sit.arenaops.in', 'https://arenaops.in', 'https://custom-host.example']) {
    for (const path of ['/', '/login/callback?code=test&state=test', '/dashboard', '/dashboard/', '/register', '/admin', '/billing', '/nested/route/']) {
      assert.equal(new URL(script[1], origin + path).href, origin + '/arena-config.js');
    }
  }
});
