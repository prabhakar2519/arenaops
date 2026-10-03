import { HttpClient } from '@angular/common/http';
import { AsyncValidatorFn, ValidatorFn, Validators } from '@angular/forms';
import { catchError, map, of, switchMap, timer } from 'rxjs';

export const trimmedEmailValidator: ValidatorFn = control => {
  const email = String(control.value || '').trim();
  return email ? Validators.email({ value: email } as typeof control) : { required: true };
};

export function customerEmailValidator(http: HttpClient): AsyncValidatorFn {
  return control => timer(400).pipe(
    switchMap(() => http.post<{ registered: boolean }>('/api/admin/customers/email-check', {
      email: String(control.value).trim().toLowerCase()
    })),
    map(result => result.registered ? { emailRegistered: true } : null),
    catchError(() => of({ emailCheckFailed: true }))
  );
}
