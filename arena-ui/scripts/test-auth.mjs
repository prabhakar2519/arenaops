import assert from 'node:assert/strict';
import { webcrypto } from 'node:crypto';
import fs from 'node:fs';
import { test } from 'node:test';
import ts from 'typescript';
import { createRequire } from 'node:module';
import { pathToFileURL } from 'node:url';
import { EventEmitter } from 'node:events';
const require = createRequire(import.meta.url);

async function loadPureSource(relative) {
  const source = fs.readFileSync(new URL(relative, import.meta.url), 'utf8');
  const compiled = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.ES2022, target: ts.ScriptTarget.ES2022 }
  }).outputText.replace("'@noble/hashes/sha256'", JSON.stringify(pathToFileURL(require.resolve('@noble/hashes/sha256')).href));
  return import('data:text/javascript;base64,' + Buffer.from(compiled).toString('base64'));
}
const { validateRuntimeConfig } = await loadPureSource('../src/environments/runtime-config.ts');
const { authorizationUrl, consumeVerifier } = await loadPureSource('../src/app/services/oidc.ts');

test('DEV identity proxy keeps browser URLs under /auth on localhost and LAN origins', () => {
  const config = require('../proxy.conf.cjs')['/auth/**'];
  for (const origin of ['http://localhost:4200', 'http://192.168.0.219:4200']) {
    const proxy = new EventEmitter();
    config.configure(proxy);
    const upstream = new EventEmitter();
    upstream.statusCode = 200;
    upstream.headers = { 'content-type': 'text/html', 'content-length': '123',
      location: 'http://localhost:9091/realms/arena-dev/login-actions/authenticate',
      'set-cookie': [
        'AUTH_SESSION_ID=example;Version=1;Path=/realms/arena-dev/;Secure;HttpOnly;SameSite=None',
        'KC_AUTH_SESSION_HASH=example;Version=1;Path=/realms/arena-dev/;Max-Age=60;Secure;SameSite=None',
        'KC_RESTART=example;Version=1;Path=/realms/arena-dev/;Secure;HttpOnly;SameSite=None'
      ] };
    let headers;
    let body;
    proxy.emit('proxyRes', upstream, { headers: { host: new URL(origin).host } }, {
      writeHead: (status, value) => { assert.equal(status, 200); headers = value; },
      end: value => { body = value.toString(); }
    });
    upstream.emit('data', Buffer.from([
      '<link href="/resources/f4bjx/login/arena-login/css/arena-login-v3.css">',
      '<script src="/resources/f4bjx/login/arena-login/js/passwordVisibility.js"></script>',
      '<form action="http://localhost:9091/realms/arena-dev/login-actions/authenticate">',
      '<a href="http://localhost:9091/auth/realms/arena-dev/protocol/openid-connect/auth">Sign in</a>',
      '<a href="https://identity.example/realms/external">External identity</a>',
      `<input value="${origin}/login/callback">`,
      '</form>'
    ].join('')));
    upstream.emit('end');
    assert.match(body, /href="\/auth\/resources\/f4bjx\/login\/arena-login\/css\/arena-login-v3\.css"/);
    assert.match(body, /src="\/auth\/resources\/f4bjx\/login\/arena-login\/js\/passwordVisibility\.js"/);
    assert.match(body, new RegExp(`action="${origin}/auth/realms/arena-dev/login-actions/authenticate"`));
    assert.match(body, new RegExp(`href="${origin}/auth/realms/arena-dev/protocol/openid-connect/auth"`));
    assert.match(body, /href="https:\/\/identity\.example\/realms\/external"/);
    assert.match(body, new RegExp(`value="${origin}/login/callback"`));
    assert.equal(headers.location, `${origin}/auth/realms/arena-dev/login-actions/authenticate`);
    for (const cookie of headers['set-cookie']) {
      assert.match(cookie, /Path=\/auth\/realms\/arena-dev\//);
      assert.match(cookie, /SameSite=Lax/);
      assert.doesNotMatch(cookie, /;Secure/);
    }
    assert.match(headers['set-cookie'][0], /HttpOnly/);
    assert.match(headers['set-cookie'][1], /Max-Age=60/);
    assert.doesNotMatch(headers['set-cookie'][1], /HttpOnly/);
    assert.match(headers['set-cookie'][2], /HttpOnly/);
    assert.ok(headers['set-cookie'].every(cookie => !/;\s*Domain=/i.test(cookie)));
    assert.equal(headers['content-length'], undefined);
  }
});

test('DEV identity proxy leaves canonical Keycloak discovery JSON unchanged', () => {
  const config = require('../proxy.conf.cjs')['/auth/**'];
  const proxy = new EventEmitter();
  config.configure(proxy);
  const upstream = new EventEmitter();
  upstream.statusCode = 200;
  upstream.headers = { 'content-type': 'application/json' };
  const discovery = '{"issuer":"http://localhost:9091/realms/arena-dev","token_endpoint":"http://localhost:9091/realms/arena-dev/protocol/openid-connect/token"}';
  let body;
  proxy.emit('proxyRes', upstream, { headers: { host: '192.168.0.219:4200' } }, {
    writeHead: status => assert.equal(status, 200),
    end: value => { body = value.toString(); }
  });
  upstream.emit('data', Buffer.from(discovery));
  upstream.emit('end');
  assert.equal(body, discovery);
});

test('insecure remote environments fail before storing OAuth state', async () => {
  for (const arenaEnv of ['sit', 'prod']) {
    const session = storage();
    await assert.rejects(authorizationUrl({ arenaEnv, keycloakRealm: 'arena-dev',
      keycloakBaseUrl: 'http://localhost:9091/auth' }, 'http://192.168.0.219:4200', session,
      { getRandomValues: webcrypto.getRandomValues.bind(webcrypto) }),
      /HTTPS/);
    assert.equal(session.getItem('arenaops.oauth.state'), null);
    assert.equal(session.getItem('arenaops.oauth.verifier'), null);
  }
});

test('DEV LAN PKCE without SubtleCrypto matches native SHA-256', async () => {
  const session = storage();
  const url = new URL(await authorizationUrl({ arenaEnv: 'dev', keycloakRealm: 'arena-dev',
    keycloakBaseUrl: 'http://localhost:9091/auth' }, 'http://192.168.0.219:4200', session,
    { getRandomValues: webcrypto.getRandomValues.bind(webcrypto) }));
  const verifier = consumeVerifier(url.searchParams.get('state'), session);
  const expected = Buffer.from(await webcrypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier))).toString('base64url');
  assert.equal(url.searchParams.get('code_challenge'), expected);
  assert.equal(url.searchParams.get('code_challenge_method'), 'S256');
  assert.equal(url.searchParams.get('redirect_uri'), 'http://192.168.0.219:4200/login/callback');
});

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
