import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { FormGroup } from '@angular/forms';
import { catchError, throwError } from 'rxjs';

export interface ApiError {
  status: number;
  errorCode: string;
  reasonCode?: string;
  description: string;
  correlationId?: string;
  fieldErrors?: Record<string, string>;
}

let scrollPending = false;

export function scrollToTopOnError(): void {
  if (typeof window === 'undefined' || scrollPending) return;
  scrollPending = true;
  window.requestAnimationFrame(() => {
    scrollPending = false;
    window.scrollTo({ top: 0, left: 0, behavior: 'auto' });
  });
}

const descriptions: Record<string, string> = {
  INVALID_REQUEST: 'The request is invalid.',
  VALIDATION_FAILED: 'The request contains invalid fields.',
  AUTHENTICATION_REQUIRED: 'Please sign in to continue.',
  AUTHENTICATION_FAILED: 'Your sign-in session is invalid or expired. Please sign in again.',
  ACCESS_DENIED: 'You do not have permission to perform this operation.',
  USER_DISABLED: 'Your user account is disabled. Please contact your administrator.',
  CUSTOMER_ACCESS_BLOCKED: 'Your ArenaOps account does not currently have access. Please contact your administrator.',
  SUBSCRIPTION_EXPIRED: 'Your ArenaOps subscription has expired. Please contact your administrator.',
  SUBSCRIPTION_ACCESS_BLOCKED: 'Your ArenaOps subscription is no longer active. Please contact your administrator.',
  PAYMENT_REQUIRED: 'Your trial has ended. Please contact your administrator to continue.',
  CUSTOMER_NOT_FOUND: 'Customer with the supplied identifier was not found.',
  CUSTOMER_ALREADY_EXISTS: 'A customer already exists with this email.',
  USER_ALREADY_EXISTS: 'An account already exists with these details.',
  INVITATION_NOT_FOUND: 'No invitation was found.',
  INVITATION_INVALID: 'The activation code is invalid.',
  INVITATION_EXPIRED: 'This invitation has expired. Please request a new invitation.',
  INVITATION_ALREADY_CONSUMED: 'This invitation has already been used.',
  INVITATION_REVOKED: 'This invitation has been revoked. Please request a new invitation.',
  INVITATION_EMAIL_MISMATCH: 'Registration email must match the invited email.',
  INVITATION_NOT_READY: 'This invitation is not available for registration.',
  SUBSCRIPTION_NOT_FOUND: 'No subscription was found for this customer.',
  SUBSCRIPTION_CANCELLED: 'Cancelled customers must be reactivated before recording payment.',
  GRACE_PERIOD_ALREADY_ACTIVE: 'Customer already has an active grace period.',
  GRACE_PERIOD_NOT_ALLOWED: 'Grace period can only be granted when payment is due.',
  INVALID_GRACE_END: 'Grace period end must be in the future.',
  INVALID_STATE: 'This operation is not allowed in the current state.',
  RESOURCE_NOT_FOUND: 'The requested resource was not found.',
  CONFLICT: 'The operation conflicts with existing data. Please refresh and try again.',
  EMAIL_DISABLED: 'Invitation email was not sent because email delivery is currently disabled.',
  EMAIL_DELIVERY_FAILED: 'The invitation email could not be sent. Please retry the invitation.',
  IDENTITY_PROVIDER_UNAVAILABLE: 'Sign-in services are temporarily unavailable. Please try again shortly.',
  IDENTITY_CREATION_FAILED: 'Your account could not be created in the sign-in service. Please try again.',
  SERVICE_UNAVAILABLE: 'ArenaOps is temporarily unavailable. Please try again shortly.',
  INTERNAL_SERVER_ERROR: 'An unexpected error occurred while processing the request.',
  METHOD_NOT_ALLOWED: 'This request method is not supported.',
  UNSUPPORTED_MEDIA_TYPE: 'This request format is not supported.',
  NOT_ACCEPTABLE: 'The requested response format is not supported.'
};

export function apiError(error: unknown): ApiError {
  const failure = error as { status?: number; name?: string; error?: unknown };
  const status = failure?.name === 'TimeoutError' ? 504 : failure?.status ?? 0;
  let body: any = failure?.error;
  if (typeof body === 'string') {
    try { body = JSON.parse(body); } catch { body = null; }
  }
  const suppliedCode = body?.errorCode;
  const fallback = status === 0 || status >= 502 ? 'SERVICE_UNAVAILABLE'
    : status === 401 ? 'AUTHENTICATION_REQUIRED'
    : status === 403 ? 'ACCESS_DENIED'
    : status === 404 ? 'RESOURCE_NOT_FOUND'
    : status === 409 ? 'CONFLICT'
    : status >= 500 ? 'INTERNAL_SERVER_ERROR' : 'INVALID_REQUEST';
  const errorCode = typeof suppliedCode === 'string' && Object.hasOwn(descriptions, suppliedCode) ? suppliedCode : fallback;
  const fieldErrors: Record<string, string> = {};
  if (errorCode === 'VALIDATION_FAILED' && body?.fieldErrors && typeof body.fieldErrors === 'object') {
    for (const [field, message] of Object.entries(body.fieldErrors)) {
      if (typeof message === 'string') fieldErrors[field] = message.slice(0, 300);
    }
  }
  return {
    status, errorCode, description: descriptions[errorCode],
    reasonCode: errorCode === 'VALIDATION_FAILED' ? 'INVALID_REQUEST_FIELDS' : undefined,
    correlationId: typeof body?.correlationId === 'string' && /^[a-f0-9-]{36}$/i.test(body.correlationId) ? body.correlationId : undefined,
    fieldErrors: Object.keys(fieldErrors).length ? fieldErrors : undefined
  };
}

export function applyFieldErrors(form: FormGroup, error: unknown): Record<string, string> {
  const fields = apiError(error).fieldErrors || {};
  if (Object.keys(fields).length) scrollToTopOnError();
  for (const field of Object.keys(fields)) {
    const control = form.get(field);
    control?.setErrors({ ...control.errors, serverValidation: true });
    control?.markAsTouched();
  }
  return fields;
}

@Injectable({ providedIn: 'root' })
export class ApiNotifications {
  readonly error = signal<ApiError | null>(null);
  dismiss(): void { this.error.set(null); }
}

export const apiErrorInterceptor: HttpInterceptorFn = (request, next) => {
  const notifications = inject(ApiNotifications);
  return next(request).pipe(catchError(error => {
    if (!request.url.startsWith('/api')) return throwError(() => error);
    const normalized = apiError(error);
    scrollToTopOnError();
    if (normalized.status === 0 || normalized.status >= 500) notifications.error.set(normalized);
    return throwError(() => new HttpErrorResponse({
      status: normalized.status,
      headers: error.headers,
      url: error.url,
      // The alias supports existing screens while all machine decisions use errorCode.
      error: { ...normalized, message: normalized.description }
    }));
  }));
};
