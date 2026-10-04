import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterLinkActive, Router } from '@angular/router';
import { AuthService } from '../services/auth';
import { ThemeService } from '../services/theme';

@Component({
    selector: 'app-header',
    standalone: true,
    imports: [CommonModule, RouterLink, RouterLinkActive], // Added CommonModule for *ngIf
    templateUrl: './header.component.html',
    styleUrls: ['./header.component.scss']
})
export class HeaderComponent implements OnInit {
    auth = inject(AuthService);
    themeService = inject(ThemeService);
    router = inject(Router);
    linkCopied = false;
    showBookingLinkPanel = false;

    ngOnInit(): void {
        if (!this.auth.isAuthenticated()) {
            return;
        }

        this.auth.fetchParlours().subscribe({
            next: (parlours) => {
                const activeParlour = this.auth.getParlourForSport(this.auth.activeSportValue) || parlours[0];
                if (activeParlour) {
                    this.auth.setParlourName(activeParlour.name);
                    this.auth.setActiveSport(activeParlour.sport || this.auth.activeSportValue);
                }
            },
            error: () => undefined
        });
        if (this.auth.hasRole('OWNER')) {
            this.auth.fetchRegistrationOptions().subscribe({ error: () => undefined });
        }
    }

    get sports(): string[] {
        return this.auth.availableSports;
    }

    get hasMultipleSports(): boolean {
        return this.sports.length > 1;
    }

    sportLabel(sport: string): string {
        if (!this.hasMultipleSports) {
            return 'Dashboard';
        }
        return sport === 'BADMINTON' ? 'Badminton' : 'Snooker';
    }

    get logoIcon(): string {
        return this.auth.activeSportValue === 'BADMINTON' ? '🏸' : '🎱';
    }

    get isStaffUser(): boolean {
        return this.auth.hasRole('STAFF');
    }

    get isPublicBookingPage(): boolean {
        return this.router.url.startsWith('/book/');
    }

    get homeTarget(): string {
        if (this.isPublicBookingPage) {
            return this.router.url;
        }
        if (this.auth.hasRole('ADMIN')) {
            return '/admin';
        }
        if (this.auth.isAuthenticated()) {
            return this.auth.activeSportValue === 'BADMINTON' ? '/badminton' : '/dashboard';
        }
        return '/';
    }

    get bookingLink(): string {
        if (this.isPublicBookingPage || !this.canShareBookingLink) {
            return '';
        }

        const parlour = this.activeParlourForBooking;
        const bookingPath = parlour?.bookingUrl || (parlour?.publicSlug ? `/book/${parlour.publicSlug}` : '');
        return bookingPath ? `${window.location.origin}${bookingPath}` : '';
    }

    get canShareBookingLink(): boolean {
        return this.auth.isAuthenticated() && !this.auth.hasRole('ADMIN') && (this.auth.hasRole('OWNER') || this.auth.hasRole('STAFF'));
    }

    get activeParlourForBooking() {
        const routeSport = this.router.url.startsWith('/badminton') ? 'BADMINTON'
            : this.router.url.startsWith('/dashboard') ? 'SNOOKER'
                : this.auth.activeSportValue;
        return this.auth.getParlourForSport(routeSport) || this.auth.parloursValue[0];
    }

    navigateToSport(sport: string): void {
        this.auth.setActiveSport(sport);
        const parlour = this.auth.getParlourForSport(sport);
        if (!parlour && this.auth.hasRole('OWNER')) {
            this.router.navigate(['/parlour-registration']);
            return;
        }
        if (parlour) {
            this.auth.setParlourName(parlour.name);
        }
        this.router.navigate([sport === 'BADMINTON' ? '/badminton' : '/dashboard']);
    }

    isSportActive(sport: string): boolean {
        const currentUrl = this.router.url;
        return sport === 'BADMINTON' ? currentUrl.startsWith('/badminton') : currentUrl.startsWith('/dashboard');
    }

    logout() {
        void this.auth.logout();
    }

    login() {
        void this.router.navigate(['/login']);
    }

    toggleTheme() {
        this.themeService.toggleTheme();
    }

    toggleBookingLinkPanel(): void {
        this.showBookingLinkPanel = !this.showBookingLinkPanel;
    }

    copyBookingLink(): void {
        if (!this.bookingLink) {
            return;
        }

        const copied = () => {
            this.linkCopied = true;
            setTimeout(() => this.linkCopied = false, 1800);
        };

        if (navigator.clipboard && window.isSecureContext) {
            navigator.clipboard.writeText(this.bookingLink).then(copied).catch(() => this.copyBookingLinkFallback(copied));
            return;
        }

        this.copyBookingLinkFallback(copied);
    }

    openBookingLink(): void {
        if (this.bookingLink) {
            window.open(this.bookingLink, '_blank', 'noopener');
        }
    }

    private copyBookingLinkFallback(copied: () => void): void {
        try {
            const input = document.createElement('input');
            input.value = this.bookingLink;
            input.setAttribute('readonly', 'true');
            input.style.position = 'fixed';
            input.style.opacity = '0';
            document.body.appendChild(input);
            input.focus();
            input.select();
            document.execCommand('copy');
            document.body.removeChild(input);
            copied();
        } catch {
            this.showBookingLinkPanel = true;
        }
    }
}
