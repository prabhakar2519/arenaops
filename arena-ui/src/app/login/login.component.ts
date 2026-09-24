import { Component, OnInit, inject } from '@angular/core';
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
export class LoginComponent implements OnInit {
  authService = inject(AuthService);
  router = inject(Router);
  accessDeniedMessage: string | null = null;
  isRedirecting = false;

  ngOnInit(): void {
    this.accessDeniedMessage = this.authService.consumeAccessDeniedMessage();

    if (this.accessDeniedMessage) {
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
      error: () => this.redirectToKeycloak()
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
    this.authService.loginWithKeycloak();
  }
}
