import { DashboardInformationService } from '../sessions/dashboard-information.service';
import { BusinessLogoComponent } from '../ui/business-logo.component';
import { Component, OnInit, inject, DestroyRef, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, Router, NavigationEnd } from '@angular/router';
import { AuthService } from '../services/auth';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ThemeService } from '../services/theme';

@Component({
    selector: 'app-header',
    standalone: true,
    imports: [CommonModule, RouterLink, BusinessLogoComponent], // Added CommonModule for *ngIf
    templateUrl: './header.component.html',
    styleUrls: ['./header.component.scss']
})
export class HeaderComponent implements OnInit {
    auth = inject(AuthService);
    readonly information = inject(DashboardInformationService);
    get canSeeNotifications():boolean {return this.auth.hasRole('OWNER')||this.auth.hasRole('MANAGER');}
    themeService = inject(ThemeService);
    router = inject(Router);
    linkCopied = false;
    showBookingLinkPanel = false;

    private destroyRef = inject(DestroyRef);
    private informationUser = '';
    academyEligible = false;
    trainingIncomplete = false;
    @HostListener('window:focus') refreshBranding(){if(this.auth.isAuthenticated()&&!this.auth.hasRole('ADMIN'))this.auth.fetchBusinessBranding().subscribe({error:()=>this.auth.setBusinessBranding(null)});}
    ngOnInit(): void {
        this.router.events.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(e=>{if(e instanceof NavigationEnd)this.refreshBranding();});
        this.auth.academyContext$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(context => {
            this.academyEligible = !!context?.eligible;
            this.trainingIncomplete = !!context?.eligible && !context.setupComplete;
            if(context)this.information.refreshTraining();
        });
        this.auth.currentUser$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(user => {
            const key=user ? `${user.username}|${user.role}|${(user.roles || []).join(',')}` : '';
            if(key!==this.informationUser){this.information.reset();this.informationUser=key;}
            if(!user||this.auth.hasRole('ADMIN')){this.auth.setBusinessBranding(null);this.information.reset();return;}
            this.refreshBranding();
            this.auth.fetchAcademyContext().subscribe({error:()=>undefined});
        });
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

    get businessName(): string {
        if (!this.auth.isAuthenticated() || this.auth.hasRole('ADMIN')) return 'ArenaOps';
        return this.auth.businessBrandingValue?.displayName?.trim() || this.auth.academyContextValue?.name?.trim()
            || this.activeParlourForBooking?.name?.trim()
            || 'Business workspace';
    }

    get businessInitial(): string {
        return this.businessName.slice(0, 1).toUpperCase();
    }

    get homeTarget(): string {
        if (this.isPublicBookingPage) return this.router.url;
        if (this.auth.hasRole('ADMIN')) return '/admin';
        return this.auth.isAuthenticated() ? '/academy/dashboard' : '/';
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
