import { Component, inject } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';

@Component({
    selector: 'app-user-registration',
    standalone: true,
    imports: [ReactiveFormsModule, CommonModule, RouterLink],
    templateUrl: './user-registration.component.html',
    styleUrls: ['./user-registration.component.scss']
})
export class UserRegistrationComponent {
    private fb = inject(FormBuilder);
    private http = inject(HttpClient);
    private router = inject(Router);

    userForm: FormGroup = this.fb.group({
        onboardingCode: ['', [Validators.required, Validators.minLength(8)]],
        username: ['', [Validators.required, Validators.minLength(3)]],
        email: ['', [Validators.required, Validators.email]],
        password: ['', [Validators.required, Validators.minLength(6)]]
    });

    isSubmitting = false;
    submissionMessage = '';
    isSuccess = false;

    onSubmit() {
        if (this.userForm.valid) {
            this.isSubmitting = true;
            this.submissionMessage = '';

            const payload = { ...this.userForm.value, role: 'OWNER' };
            this.http.post('/api/registration/activate', payload)
                .subscribe({
                    next: (response) => {
                        console.log('User registration successful', response);
                        this.isSuccess = true;
                        this.submissionMessage = 'User registered successfully! Redirecting to login...';
                        this.isSubmitting = false;
                        this.userForm.reset();

                        // Redirect to Login component after 1.5s
                        setTimeout(() => {
                            this.router.navigate(['/login']);
                        }, 1500);
                    },
                    error: (error) => {
                        console.error('User registration failed', error);
                        this.isSuccess = false;
                        this.submissionMessage = error.error?.message || 'Registration failed. Please try again.';
                        this.isSubmitting = false;
                    }
                });
        } else {
            this.markFormGroupTouched(this.userForm);
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
