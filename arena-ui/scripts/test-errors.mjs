import '@angular/compiler';
import assert from 'node:assert/strict';
import { test } from 'node:test';
import fs from 'node:fs';
import { createRequire } from 'node:module';
import { pathToFileURL } from 'node:url';
import ts from 'typescript';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { HttpErrorResponse, HttpRequest } from '@angular/common/http';
import { createEnvironmentInjector, runInInjectionContext } from '@angular/core';
import { firstValueFrom, of, throwError } from 'rxjs';

// Exercise the actual TypeScript helpers with Angular forms and HTTP objects, without a browser harness.
const require = createRequire(import.meta.url);
async function loadSource(relative) {
  const source = fs.readFileSync(new URL(relative, import.meta.url), 'utf8');
  let compiled = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.ES2022, target: ts.ScriptTarget.ES2022, experimentalDecorators: true }
  }).outputText;
  compiled = compiled.replace(/from ['"]([^'"]+)['"]/g, (_match, module) =>
    `from '${pathToFileURL(require.resolve(module)).href}'`);
  return import('data:text/javascript;base64,' + Buffer.from(compiled).toString('base64'));
}
const { apiError, applyFieldErrors, ApiNotifications, apiErrorInterceptor } = await loadSource('../src/app/api-error.ts');
const { customerEmailValidator, trimmedEmailValidator } = await loadSource('../src/app/admin/customer-email.validator.ts');

test('stable codes select readable messages and raw provider bodies are never displayed', () => {
  const duplicate = apiError(new HttpErrorResponse({ status: 409, error: {
    errorCode: 'CUSTOMER_ALREADY_EXISTS', description: 'password=secret SELECT * FROM customer'
  } }));
  assert.equal(duplicate.description, 'A customer already exists with this email.');
  for (const error of [new HttpErrorResponse({ status: 500, error: '<html>private SQL stack trace</html>' }),
    new HttpErrorResponse({ status: 403, error: { message: 'client_secret=hidden' } })]) {
    assert.doesNotMatch(apiError(error).description, /private|SQL|client_secret|html/);
  }
  assert.equal(apiError({ name: 'TimeoutError' }).errorCode, 'SERVICE_UNAVAILABLE');
});

test('server field errors mark the matching form control invalid and edits clear them', () => {
  const form = new FormGroup({ email: new FormControl('owner@example.com', Validators.email) });
  const fields = applyFieldErrors(form, new HttpErrorResponse({ status: 400, error: {
    errorCode: 'VALIDATION_FAILED', fieldErrors: { email: 'must be a valid email address' }
  } }));
  assert.equal(fields.email, 'must be a valid email address');
  assert.equal(form.controls.email.hasError('serverValidation'), true);
  form.controls.email.setValue('new@example.com');
  assert.equal(form.valid, true);
});

test('HTTP interceptor normalizes errors and publishes one readable outage notification', async () => {
  const injector = createEnvironmentInjector([ApiNotifications]);
  try {
    const response = runInInjectionContext(injector, () => apiErrorInterceptor(new HttpRequest('GET', '/api/customers'),
      () => throwError(() => new HttpErrorResponse({ status: 503, error: 'internal-host:7701' }))));
    await assert.rejects(firstValueFrom(response), error => {
      assert.equal(error.error.errorCode, 'SERVICE_UNAVAILABLE');
      assert.equal(error.error.message, error.error.description);
      return true;
    });
    assert.equal(injector.get(ApiNotifications).error().errorCode, 'SERVICE_UNAVAILABLE');
    injector.get(ApiNotifications).dismiss();
    assert.equal(injector.get(ApiNotifications).error(), null);
  } finally { injector.destroy(); }
});

test('email check accepts pasted whitespace, cancels stale checks, and reports duplicates', async () => {
  const calls = [];
  const http = { post: (_url, body) => { calls.push(body.email); return of({ registered: body.email === 'owner@example.com' }); } };
  const control = new FormControl('', [Validators.required, trimmedEmailValidator], [customerEmailValidator(http)]);
  control.setValue('old@example.com');
  control.setValue(' Owner@Example.com ');
  assert.equal(control.pending, true);
  await new Promise(resolve => setTimeout(resolve, 450));
  assert.deepEqual(calls, ['owner@example.com']);
  assert.equal(control.hasError('emailRegistered'), true);
  control.setValue('invalid-email');
  assert.equal(control.hasError('email'), true);
  assert.deepEqual(calls, ['owner@example.com']);
});

test('unavailable email check blocks submission with a retryable error', async () => {
  const control = new FormControl('owner@example.com', trimmedEmailValidator,
    customerEmailValidator({ post: () => throwError(() => new HttpErrorResponse({ status: 503 })) }));
  await new Promise(resolve => setTimeout(resolve, 450));
  assert.equal(control.hasError('emailCheckFailed'), true);
  assert.equal(control.invalid, true);
});
