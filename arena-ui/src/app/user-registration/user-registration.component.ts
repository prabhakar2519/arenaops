import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { timeout } from 'rxjs';
import { apiError, applyFieldErrors, scrollToTopOnError } from '../api-error';

@Component({
    selector: 'app-user-registration',
    host: { '(focusout)': 'scrollToValidationError()' },
    standalone: true,
    imports: [ReactiveFormsModule, CommonModule, RouterLink],
    templateUrl: './user-registration.component.html',
    styleUrls: ['./user-registration.component.scss']
})
export class UserRegistrationComponent implements OnInit {
    private fb = inject(FormBuilder);
    private http = inject(HttpClient);
    private router = inject(Router);
    private route = inject(ActivatedRoute);
    private destroyRef = inject(DestroyRef);

    userForm: FormGroup = this.fb.group({
        onboardingCode: ['', [Validators.required, Validators.minLength(8)]],
        username: ['', [Validators.required, Validators.minLength(3)]],
        email: ['', [Validators.required, Validators.email]],
        password: ['', [Validators.required, Validators.minLength(6)]]
    });

    isSubmitting = false;
    fieldErrors: Record<string, string> = {};
    submissionMessage = '';
    isSuccess = false;

    isInvitationLink = false;
    isCheckingInvitation = false;
    invitationReady = false;
    invitedEmail = '';
    organizationName = '';

    ngOnInit(): void {
        // URL fragments are not sent in HTTP requests or Referer headers.
        const params = new URLSearchParams(this.route.snapshot.fragment ?? '');
        this.isInvitationLink = params.has('activationCode') || params.has('email');
        if (!this.isInvitationLink) return;
        const activationCode = params.get('activationCode') ?? '';
        const email = params.get('email') ?? '';
        if (!activationCode || !email) {
            this.submissionMessage = 'This invitation link is incomplete. Please request a new invitation.';
            scrollToTopOnError();
            return;
        }
        this.isCheckingInvitation = true;
        this.http.post<{ invitedEmail: string; organizationName: string }>(
            '/api/registration/validate-invitation', { activationCode, email }
        ).pipe(timeout(15000), takeUntilDestroyed(this.destroyRef)).subscribe({
            next: invitation => {
                this.invitedEmail = invitation.invitedEmail;
                this.organizationName = invitation.organizationName;
                this.userForm.patchValue({ onboardingCode: activationCode, email: invitation.invitedEmail });
                this.invitationReady = true;
                this.isCheckingInvitation = false;
            },
            error: error => {
                this.isCheckingInvitation = false;
                this.submissionMessage = apiError(error).description;
                scrollToTopOnError();
            }
        });
    }

    onSubmit() {
        if (this.isSubmitting || this.isSuccess || (this.isInvitationLink && !this.invitationReady)) return;
        if (this.userForm.valid) {
            this.isSubmitting = true;
            this.submissionMessage = '';
            this.fieldErrors = {};

            const payload = { ...this.userForm.value, role: 'OWNER' };
            this.http.post('/api/registration/activate', payload)
                .pipe(timeout(20000), takeUntilDestroyed(this.destroyRef)).subscribe({
                    next: () => {
                        this.isSuccess = true;
                        this.submissionMessage = 'User registered successfully! Redirecting to login...';
                        this.isSubmitting = false;
                        this.userForm.reset();
                        // Remove the used invitation from browser history before redirecting.
                        void this.router.navigate([], { relativeTo: this.route, fragment: '', replaceUrl: true });

                        // Redirect to Login component after 1.5s
                        setTimeout(() => {
                            this.router.navigate(['/login'], { replaceUrl: true });
                        }, 1500);
                    },
                    error: (error) => {
                        scrollToTopOnError();
                        this.isSuccess = false;
                        this.fieldErrors = applyFieldErrors(this.userForm, error);
                        this.submissionMessage = apiError(error).description;
                        this.isSubmitting = false;
                    }
                });
        } else {
            this.markFormGroupTouched(this.userForm);
            scrollToTopOnError();
        }
    }

    scrollToValidationError(): void {
        if (Object.values(this.userForm.controls).some(control => control.invalid && control.touched)) {
            scrollToTopOnError();
        }
    }

    private markFormGroupTouched(formGroup: FormGroup) {
        Object.values(formGroup.controls).forEach(control => {
            control.markAsTouched();
            if (control instanceof FormGroup) {
                this.markFormGroupTouched(control);
            }
        });
    }
}
