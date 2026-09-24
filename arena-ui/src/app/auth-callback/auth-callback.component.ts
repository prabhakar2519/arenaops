import { Component, OnInit, inject } from '@angular/core';
import { Router, ActivatedRoute } from '@angular/router';
import { AuthService } from '../services/auth';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-auth-callback',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="callback-container">
      <div class="callback-card">
        <div class="spinner"></div>
        <h2>Authenticating...</h2>
        <p>Please wait while we complete your login.</p>
        <p *ngIf="errorMessage" class="error">{{ errorMessage }}</p>
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
    this.route.queryParams.subscribe(params => {
      const code = params['code'];

      if (code) {
        console.log('Authorization code received, exchanging for token...');

        // Exchange the code for a token via the backend
        this.authService.exchangeCodeForToken(code).subscribe({
          next: (user) => {
            console.log('Authentication successful', user);
            this.handleNavigation(user);
          },
          error: (error) => {
            console.error('Token exchange failed', error);
            if (error.status === 403) {
              this.redirectAfterAccessDenied(error);
              return;
            }
            this.errorMessage = 'Authentication failed. Please try again.';
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
            if (error.status === 403) {
              this.redirectAfterAccessDenied(error);
              return;
            }
            this.errorMessage = 'Session expired. Please login again.';
            this.redirectToLogin();
          }
        });
      }
    });
  }

  private handleNavigation(user: any) {
    this.authService.setParlours([]);
    this.authService.setParlourName('ARENAOPS');
    this.authService.setActiveSport('SNOOKER');
    const target = user?.role === 'ADMIN' ? '/admin' : '/';
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
  }

  private redirectToLogin() {
    setTimeout(() => {
      this.router.navigate(['/login'], { replaceUrl: true });
    }, 3000);
  }

  private accessDeniedMessage(error: any): string {
    const body = error?.error;
    const reason = typeof body === 'string'
      ? body
      : body?.message || body?.error_description || body?.error;

    return reason
      ? `Access denied: ${reason}`
      : 'This account does not currently have access to ArenaOps. Please contact an administrator.';
  }

  private redirectAfterAccessDenied(error: any): void {
    const message = this.accessDeniedMessage(error);
    this.authService.logoutAfterAccessDenied(message);
    void this.router.navigate(['/login'], { replaceUrl: true });
  }
}
