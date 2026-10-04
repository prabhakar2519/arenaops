import { Component, OnInit, inject, signal } from '@angular/core';
import { Router, RouterOutlet } from '@angular/router';
import { HeaderComponent } from './header/header.component';
import { AuthService } from './services/auth';
import { CommonModule } from '@angular/common';
import { ApiNotifications } from './api-error';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, HeaderComponent, CommonModule],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App implements OnInit {
  readonly notifications = inject(ApiNotifications);
  protected readonly title = signal('arena-ui');
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  ngOnInit(): void {
    if (this.auth.consumeReturnToLogin()) {
      void this.router.navigate(['/login'], { replaceUrl: true });
      return;
    }

    if (this.auth.isAuthenticated() && !window.location.pathname.startsWith('/login')) {
      this.auth.validateSession().subscribe({
        error: () => void this.router.navigate(['/login'], { replaceUrl: true })
      });
    }
  }
}
