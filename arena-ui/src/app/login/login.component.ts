import { scrollToTopOnError } from '../api-error';
import { Component, OnInit, OnDestroy, inject } from '@angular/core';
import { Subscription } from 'rxjs';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { AuthService } from '../services/auth';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss'
})
export class LoginComponent implements OnInit, OnDestroy {
  authService = inject(AuthService);
  router = inject(Router);
  accessDeniedMessage: string | null = null;
  isRedirecting = false;
  private availabilityCheck?: Subscription;
  private redirectTimer?: ReturnType<typeof setTimeout>;

  ngOnInit(): void {
    this.accessDeniedMessage = this.authService.consumeAccessDeniedMessage();

    if (this.accessDeniedMessage) {
      scrollToTopOnError();
      return;
    }

    if (!this.authService.isAuthenticated()) {
      this.redirectToKeycloak();
      return;
    }

    this.authService.validateSession().subscribe({
      next: (user) => {
        const target = user.role === 'ADMIN' ? '/admin' : '/';
        this.router.navigate([target], { replaceUrl: true });
      },
      error: error => {
        if (error?.status === 0 || error?.status >= 500 || error?.name === 'TimeoutError') {
          this.accessDeniedMessage = 'ArenaOps is temporarily unavailable. Please try again shortly.';
          scrollToTopOnError();
          return;
        }
        this.redirectToKeycloak();
      }
    });
  }

  loginWithKeycloak() {
    this.redirectToKeycloak();
  }

  private redirectToKeycloak(): void {
    if (this.isRedirecting) {
      return;
    }
    this.isRedirecting = true;
    this.accessDeniedMessage = null;
    this.availabilityCheck = this.authService.checkAvailability().subscribe({
      next: () => {
        this.redirectTimer = setTimeout(() => {
          this.isRedirecting = false;
          this.accessDeniedMessage = 'The sign-in page could not be opened. Please try again.';
          scrollToTopOnError();
        }, 10000);
        void this.authService.loginWithKeycloak().catch(() => {
          this.isRedirecting = false;
          this.accessDeniedMessage = 'The sign-in page could not be opened. Please try again.';
          scrollToTopOnError();
        });
      },
      error: () => {
        this.isRedirecting = false;
        this.accessDeniedMessage = 'ArenaOps is temporarily unavailable. Please try again shortly.';
        scrollToTopOnError();
      }
    });
  }

  ngOnDestroy(): void {
    this.availabilityCheck?.unsubscribe();
    if (this.redirectTimer) clearTimeout(this.redirectTimer);
  }
}
