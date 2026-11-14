import {clubHostname} from './checkin/public-club.service';
import { Component, OnInit, inject, signal } from '@angular/core';
import { Router, RouterOutlet } from '@angular/router';
import { HeaderComponent } from './header/header.component';
import { AuthService } from './services/auth';
import { CommonModule } from '@angular/common';
import {UiFeedbackComponent} from './ui/ui-feedback.component';
import { SidebarComponent } from './sidebar/sidebar.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, HeaderComponent, CommonModule, SidebarComponent, UiFeedbackComponent],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App implements OnInit {
  protected readonly title = signal('arena-ui');
  readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  get publicCheckin():boolean {return window.location.pathname.startsWith('/public/')||clubHostname()||this.router.url.split(/[?#]/)[0]==='/check-in'||window.location.pathname==='/check-in';}
  get publicLayout():boolean {return this.publicCheckin||window.location.pathname==='/academy/public-site/preview';}
  ngOnInit(): void {
    if(this.publicCheckin)return;
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
