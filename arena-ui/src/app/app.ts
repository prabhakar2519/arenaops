import { Component, OnInit, inject, signal } from '@angular/core';
import { Router, RouterOutlet } from '@angular/router';
import { HeaderComponent } from './header/header.component';
import { AuthService } from './services/auth';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, HeaderComponent],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App implements OnInit {
  protected readonly title = signal('arena-ui');
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  ngOnInit(): void {
    if (this.auth.consumeReturnToLogin()) {
      void this.router.navigate(['/login'], { replaceUrl: true });
      return;
    }

    if (this.auth.isAuthenticated() && !window.location.pathname.startsWith('/login/callback')) {
      this.auth.validateSession().subscribe({
        error: () => void this.router.navigate(['/login'], { replaceUrl: true })
      });
    }
  }
}
