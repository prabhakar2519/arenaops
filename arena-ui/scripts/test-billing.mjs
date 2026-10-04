import '@angular/compiler';
import assert from 'node:assert/strict';
import { test } from 'node:test';
import fs from 'node:fs';
import { createRequire } from 'node:module';
import { pathToFileURL } from 'node:url';
import ts from 'typescript';
import { HttpClient } from '@angular/common/http';
import { createEnvironmentInjector, runInInjectionContext } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
const require = createRequire(import.meta.url);
const urls = new Map();
async function load(relative, aliases = {}) {
  let code = ts.transpileModule(fs.readFileSync(new URL(relative, import.meta.url), 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.ES2022, target: ts.ScriptTarget.ES2022, experimentalDecorators: true }
  }).outputText;
  code = code.replace(/from ['"]([^'"]+)['"]/g, (_m, module) => `from '${aliases[module] || pathToFileURL(require.resolve(module)).href}'`);
  const url = 'data:text/javascript;base64,' + Buffer.from(code).toString('base64'); urls.set(relative, url);
  return import(url);
}
const { BillingService, billingRequestId } = await load('../src/app/billing/billing.service.ts');
await load('../src/app/api-error.ts');
const { BillingComponent } = await load('../src/app/billing/billing.component.ts', {
  './billing.service': urls.get('../src/app/billing/billing.service.ts'), '../api-error': urls.get('../src/app/api-error.ts')
});
const plan = { id: 'STANDARD', cycle: 'MONTHLY', name: 'Standard', baseAmount: 1000, taxAmount: 180, total: 1180, currency: 'INR' };
const order = { id: 'order-1', status: 'CREATED', total: 1180, attemptId: null, expiresAt: '2099-01-01T00:00:00' };
const overview = { status: 'TRIAL', mockEnabled: true, plans: [plan], pendingOrder: null };
function component(api, id = null) {
  const navigation = [];
  const injector = createEnvironmentInjector([
    { provide: BillingService, useValue: api },
    { provide: Router, useValue: { navigate: (...args) => { navigation.push(args); return Promise.resolve(true); } } },
    { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: { get: () => id } } } }
  ]);
  const value = runInInjectionContext(injector, () => new BillingComponent());
  return { value, navigation, injector };
}
test('API requests omit tenant and amounts and encode order IDs', () => {
  const calls = [];
  const injector = createEnvironmentInjector([{ provide: HttpClient, useValue: {
    get: url => { calls.push({ url }); return of({}); },
    post: (url, body) => { calls.push({ url, body }); return of({}); }
  } }]);
  const service = runInInjectionContext(injector, () => new BillingService());
  service.create('STANDARD', 'MONTHLY', 'request-id');
  assert.deepEqual(calls[0].body, { planId: 'STANDARD', cycle: 'MONTHLY', requestId: 'request-id' });
  service.order('a/b'); assert.equal(calls[1].url, '/api/billing/orders/a%2Fb');
  service.simulate({ id: 'order', attemptId: 'attempt', total: 999 }, 'PAID');
  assert.deepEqual(calls[2].body, { attemptId: 'attempt', outcome: 'PAID' });
  injector.destroy();
});
test('refresh restores a URL order and ignores cached browser amounts', () => {
  let requested;
  const { value, injector } = component({ overview: () => of(overview), order: id => { requested = id; return of(order); } }, 'order-1');
  value.ngOnInit(); assert.equal(requested, 'order-1'); assert.equal(value.order.total, 1180); assert.equal(value.busy, false);
  injector.destroy();
});
test('unfinished checkout resumes from server overview', () => {
  const { value, injector } = component({ overview: () => of({ ...overview, pendingOrder: { ...order, status: 'PAYMENT_PENDING' } }) });
  value.ngOnInit(); assert.equal(value.order.status, 'PAYMENT_PENDING'); injector.destroy();
});
test('selection blocks duplicate clicks and reuses request id after failed network response', () => {
  const pending = new Subject(); const keys = [];
  const { value, injector } = component({ create: (_p, _c, key) => { keys.push(key); return pending; } });
  value.select(plan); value.select(plan); assert.equal(keys.length, 1);
  pending.error({ status: 503 }); assert.equal(value.busy, false);
  value.select(plan); assert.equal(keys[0], keys[1]); assert.match(value.error, /unavailable/);
  injector.destroy();
});
test('failure retry gets a new authoritative attempt and success refreshes entitlement', () => {
  const { value, injector } = component({
    checkout: () => of({ ...order, status: 'PAYMENT_PENDING', attemptId: 'new-attempt' }),
    simulate: (o, result) => { assert.equal(o.attemptId, 'new-attempt'); return of({ ...o, status: result }); },
    overview: () => of({ ...overview, status: 'ACTIVE' })
  });
  value.overview = overview; value.order = { ...order, status: 'FAILED', attemptId: 'old-attempt' };
  value.checkout(); assert.equal(value.order.attemptId, 'new-attempt');
  value.simulate('PAID'); assert.equal(value.order.status, 'PAID'); assert.equal(value.overview.status, 'ACTIVE');
  injector.destroy();
});
test('cancellation clears URL and production disables simulator calls', () => {
  let cancelled = false; let simulated = false;
  const { value, navigation, injector } = component({ cancel: () => { cancelled = true; return of({ ...order, status: 'CANCELLED' }); },
    simulate: () => { simulated = true; return of(order); } });
  value.order = order; value.back(); assert.equal(cancelled, true); assert.equal(value.order, null); assert.deepEqual(navigation[0][1].queryParams, {});
  value.order = { ...order, attemptId: 'a' }; value.overview = { ...overview, mockEnabled: false };
  value.simulate('PAID'); assert.equal(simulated, false); injector.destroy();
});
test('invalid/unauthorized order shows sanitized errors and expired order disables actions', () => {
  const { value, injector } = component({ overview: () => of(overview), order: () => throwError(() => ({ status: 404, error: { message: 'private SQL' } })) }, 'foreign');
  value.reload(); assert.doesNotMatch(value.error, /SQL|private/); assert.equal(value.order, null);
  value.order = { ...order, expiresAt: '2000-01-01T00:00:00' }; assert.equal(value.expired, true); injector.destroy();
});

test('request UUID fallback supports HTTP development LAN origins', () => {
  const id = billingRequestId({ getRandomValues: values => { values.fill(0); return values; } });
  assert.match(id, /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-8[0-9a-f]{3}-[0-9a-f]{12}$/);
});
