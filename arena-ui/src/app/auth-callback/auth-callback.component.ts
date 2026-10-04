import { Component, OnInit, inject } from '@angular/core';
import { Router, ActivatedRoute } from '@angular/router';
import { AuthService } from '../services/auth';
import { CommonModule } from '@angular/common';
import { apiError, scrollToTopOnError } from '../api-error';

@Component({
  selector: 'app-auth-callback',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="callback-container">
      <div class="callback-card">
        <div *ngIf="!errorMessage" class="spinner"></div>
        <h2>{{ errorMessage ? 'Unable to complete sign in' : 'Authenticating...' }}</h2>
        <p *ngIf="!errorMessage">Please wait while we complete your login.</p>
        <p *ngIf="errorMessage" class="error" role="alert">{{ errorMessage }}</p>
        <button *ngIf="errorMessage" type="button" (click)="retryLogin()">Try again</button>
      </div>
    </div>
  `,
  styles: [`
    .callback-container {
      display: flex;
      justify-content: center;
      align-items: center;
      min-height: 80vh;
      padding: 2rem;
    }

    .callback-card {
      background: rgba(0, 0, 0, 0.7);
      backdrop-filter: blur(10px);
      border: 1px solid var(--snooker-green-light);
      border-radius: 16px;
      padding: 3rem;
      text-align: center;
      max-width: 500px;
    }

    .spinner {
      border: 4px solid rgba(255, 255, 255, 0.1);
      border-top: 4px solid var(--accent-gold);
      border-radius: 50%;
      width: 50px;
      height: 50px;
      animation: spin 1s linear infinite;
      margin: 0 auto 1.5rem;
    }

    @keyframes spin {
      0% { transform: rotate(0deg); }
      100% { transform: rotate(360deg); }
    }

    h2 {
      color: var(--accent-gold);
      margin-bottom: 0.5rem;
    }

    p {
      color: var(--text-muted);
    }

    .error {
      color: #ff4444;
      margin-top: 1rem;
    }
  `]
})
export class AuthCallbackComponent implements OnInit {
  private authService = inject(AuthService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);

  errorMessage = '';

  ngOnInit() {
    // Extract the authorization code from the URL query parameters
    const params = this.route.snapshot.queryParams;
      const code = params['code'];

      if (code) {
        console.log('Authorization code received, exchanging for token...');

        // Exchange the code for a token via the backend
        this.authService.exchangeCodeForToken(code, params['state']).subscribe({
          next: (user) => {
            console.log('Authentication successful', user);
            this.handleNavigation(user);
          },
          error: (error) => {
            console.error('Token exchange failed', error);
            if (this.handleUnavailable(error)) return;
            if (error.status === 403) {
              this.redirectAfterAccessDenied(error);
              return;
            }
            this.errorMessage = 'Authentication failed. Please try again.';
            scrollToTopOnError();
            this.redirectToLogin();
          }
        });
      } else {
        // No code in URL, try to validate existing session
        console.log('No code found, validating existing session...');
        this.authService.validateSession().subscribe({
          next: (user) => {
            console.log('Session validated', user);
            this.handleNavigation(user);
          },
          error: (error) => {
            console.error('Session validation failed', error);
            if (this.handleUnavailable(error)) return;
            if (error.status === 403) {
              this.redirectAfterAccessDenied(error);
              return;
            }
            this.errorMessage = 'Session expired. Please login again.';
            scrollToTopOnError();
            this.redirectToLogin();
          }
        });
      }
  }

  retryLogin(): void {
    // Authorization codes are single-use; restart login instead of replaying one.
    void this.router.navigate(['/login'], { replaceUrl: true });
  }

  private handleUnavailable(error: any): boolean {
    if (error?.status === 0 || error?.status >= 500 || error?.name === 'TimeoutError') {
      this.errorMessage = 'ArenaOps is temporarily unavailable. Please try again shortly.';
      scrollToTopOnError();
      return true;
    }
    return false;
  }

  private handleNavigation(user: any) {
    this.authService.setParlours([]);
    this.authService.setParlourName('ARENAOPS');
    this.authService.setActiveSport('SNOOKER');
    const target = user?.role === 'ADMIN' ? '/admin' : user?.role === 'OWNER' ? '/billing' : '/';
    this.router.navigate([target], { replaceUrl: true });
  }

  private handleWorkspaceLoadError(error: any): void {
    if (error?.status === 401 || error?.status === 403) {
      const message = error?.status === 403
        ? this.accessDeniedMessage(error)
        : 'Your session expired. Please login again.';
      this.authService.logoutAfterAccessDenied(message);
      void this.router.navigate(['/login'], { replaceUrl: true });
      return;
    }

    this.errorMessage = 'Login succeeded, but the local workspace could not be loaded. Please check the core service and try again.';
    scrollToTopOnError();
  }

  private redirectToLogin() {
    setTimeout(() => {
      this.router.navigate(['/login'], { replaceUrl: true });
    }, 3000);
  }

  private accessDeniedMessage(error: any): string {
    return apiError(error).description;
  }

  private redirectAfterAccessDenied(error: any): void {
    const message = this.accessDeniedMessage(error);
    this.authService.logoutAfterAccessDenied(message);
    void this.router.navigate(['/login'], { replaceUrl: true });
  }
}
