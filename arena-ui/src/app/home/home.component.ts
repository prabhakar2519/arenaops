import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AuthService } from '../services/auth';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Subscription, forkJoin, interval, of } from 'rxjs';
import { catchError } from 'rxjs/operators';

interface ParlourSummary {
  id: number;
  name: string;
  sport?: string;
}

interface HomeSlide {
  src: string;
  alt: string;
  sport: string;
}

interface SportSnapshot {
  parlourId: number;
  parlourName: string;
  sport: string;
  totalAreas: number;
  runningCount: number;
  availableCount: number;
  unsettledCount: number;
  currentRevenue: number;
  collectedRevenue: number;
  totalRevenue: number;
  route: string;
}

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss'
})
export class HomeComponent implements OnInit, OnDestroy {
  authService = inject(AuthService);
  private http = inject(HttpClient);
  private router = inject(Router);
  private slideshowSubscription?: Subscription;
  private snapshotRefreshSubscription?: Subscription;
  private readonly browserTimeZone = Intl.DateTimeFormat().resolvedOptions().timeZone;

  currentSlide = 0;
  bannerTitle = 'ARENAOPS';
  bannerSubtitle = 'Manage snooker tables and badminton courts from one smooth sports workspace.';
  sportSnapshots: SportSnapshot[] = [];
  isLoadingSnapshots = false;
  private readonly allSlides: HomeSlide[] = [
    { src: '/slide1.jpg', alt: 'Snooker table action', sport: 'SNOOKER' },
    { src: '/images/games/badminton/badminton-1.jpeg', alt: 'Badminton action player', sport: 'BADMINTON' },
    { src: '/slide2.jpg', alt: 'Snooker championship atmosphere', sport: 'SNOOKER' },
    { src: '/images/games/badminton/badminton-2.jpg', alt: 'Badminton shuttle court view', sport: 'BADMINTON' },
    { src: '/slide3.jpg', alt: 'Snooker precision shot', sport: 'SNOOKER' }
  ];
  slides: HomeSlide[] = [...this.allSlides];

  ngOnInit() {
    if (this.authService.hasRole('ADMIN')) {
      void this.router.navigate(['/admin'], { replaceUrl: true });
      return;
    }

    this.updateSlidesForCurrentUser();
    this.slideshowSubscription = interval(5000).subscribe(() => {
      this.nextSlide();
    });
    if (this.authService.isAuthenticated()) {
      this.snapshotRefreshSubscription = interval(30000).subscribe(() => {
        this.loadSportSnapshots(this.authService.parloursValue);
      });
    }
  }

  ngOnDestroy() {
    this.slideshowSubscription?.unsubscribe();
    this.snapshotRefreshSubscription?.unsubscribe();
  }

  nextSlide() {
    this.currentSlide = (this.currentSlide + 1) % this.slides.length;
  }

  private updateSlidesForCurrentUser() {
    if (!this.authService.isAuthenticated()) {
      this.slides = [...this.allSlides];
      this.bannerTitle = 'ARENAOPS';
      this.bannerSubtitle = 'Manage snooker tables and badminton courts from one smooth sports workspace.';
      this.sportSnapshots = [];
      return;
    }

    this.authService.fetchParlours().subscribe({
      next: (parlours) => {
        const preferredParlour = this.resolvePreferredParlour(parlours || []);
        if (preferredParlour) {
          this.authService.setParlourName(preferredParlour.name);
        }
        const sports = [...new Set(
          (parlours || [])
            .map(parlour => (parlour.sport || 'SNOOKER').trim().toUpperCase())
            .filter(Boolean)
        )];

        this.bannerTitle = preferredParlour?.name || 'ARENAOPS';
        this.bannerSubtitle = this.buildBannerSubtitle(parlours || [], sports);

        if (sports.length === 1) {
          this.slides = this.allSlides.filter(slide => slide.sport === sports[0]);
        } else if (sports.length > 1) {
          this.slides = [...this.allSlides];
        } else {
          this.slides = [...this.allSlides];
        }

        this.currentSlide = 0;
        this.loadSportSnapshots(parlours || []);
      },
      error: () => {
        this.slides = [...this.allSlides];
        this.bannerTitle = 'ARENAOPS';
        this.bannerSubtitle = 'Manage snooker tables and badminton courts from one smooth sports workspace.';
        this.sportSnapshots = [];
        this.currentSlide = 0;
      }
    });
  }

  get hasSnapshots(): boolean {
    return this.sportSnapshots.length > 0;
  }

  get totalRunningCount(): number {
    return this.sportSnapshots.reduce((sum, snapshot) => sum + snapshot.runningCount, 0);
  }

  get totalAvailableCount(): number {
    return this.sportSnapshots.reduce((sum, snapshot) => sum + snapshot.availableCount, 0);
  }

  get totalTodayRevenue(): number {
    return this.sportSnapshots.reduce((sum, snapshot) => sum + snapshot.currentRevenue, 0);
  }

  get totalCollectedRevenue(): number {
    return this.sportSnapshots.reduce((sum, snapshot) => sum + snapshot.collectedRevenue, 0);
  }

  get totalRevenue(): number {
    return this.sportSnapshots.reduce((sum, snapshot) => sum + snapshot.totalRevenue, 0);
  }

  truncateAmount(value: number | null | undefined): number {
    return Math.trunc(Number(value || 0));
  }

  formatSportName(sport: string): string {
    const normalizedSport = this.normalizeSport(sport);
    if (normalizedSport === 'BADMINTON') return 'Badminton';
    if (normalizedSport === 'SNOOKER') return 'Snooker';
    return normalizedSport
      .split(/[_\s]+/)
      .filter(Boolean)
      .map(token => token.charAt(0) + token.slice(1).toLowerCase())
      .join(' ');
  }

  getSurfaceLabel(snapshot: SportSnapshot): string {
    const sport = this.normalizeSport(snapshot.sport);
    if (sport === 'BADMINTON') return 'Courts';
    if (sport === 'SNOOKER') return 'Tables';
    return 'Play Areas';
  }

  openSport(snapshot: SportSnapshot): void {
    this.authService.setActiveSport(snapshot.sport);
    this.authService.setParlourName(snapshot.parlourName);
    this.router.navigate([snapshot.route]);
  }

  private loadSportSnapshots(parlours: ParlourSummary[]) {
    if (!this.authService.isAuthenticated()) {
      this.sportSnapshots = [];
      return;
    }

    if (!parlours || parlours.length === 0) {
      this.sportSnapshots = [];
      return;
    }

    this.isLoadingSnapshots = true;
    const requests = parlours.map(parlour => {
      const sport = this.normalizeSport(parlour.sport);
      const params = {
        sport,
        timeZone: this.browserTimeZone
      };

      return forkJoin({
        tables: this.http.get<any[]>(`/api/game-tables/parlour/${parlour.id}`, { params: { sport } }).pipe(catchError(() => of([]))),
        rateCards: this.http.get<any[]>(`/api/rate-cards/parlour/${parlour.id}`).pipe(catchError(() => of([]))),
        activeSessions: this.http.get<any[]>(`/api/sessions/parlour/${parlour.id}/active`, { params: { sport } }).pipe(catchError(() => of([]))),
        recentSessions: this.http.get<any[]>(`/api/sessions/parlour/${parlour.id}/recent`, { params: { sport } }).pipe(catchError(() => of([]))),
        dailyRevenue: this.http.get<number>(`/api/sessions/parlour/${parlour.id}/daily-revenue`, { params }).pipe(catchError(() => of(0))),
        dailyPaidRevenue: this.http.get<number>(`/api/sessions/parlour/${parlour.id}/daily-paid-revenue`, { params }).pipe(catchError(() => of(0)))
      }).pipe(
        catchError(() => of({
          tables: [],
          rateCards: [],
          activeSessions: [],
          recentSessions: [],
          dailyRevenue: 0,
          dailyPaidRevenue: 0
        }))
      );
    });

    forkJoin(requests).subscribe({
      next: (results) => {
        this.sportSnapshots = results.map((result, index) => {
          const parlour = parlours[index];
          const sport = this.normalizeSport(parlour.sport);
          const totalAreas = (result.tables || []).length;
          const runningCount = (result.activeSessions || []).length;
          const unsettledCount = (result.recentSessions || []).filter(session =>
            (session?.status || '').toUpperCase() === 'UNCONFIRMED'
          ).length;
          const currentRevenue = this.calculateCurrentRevenue(
            sport,
            result.tables || [],
            result.activeSessions || [],
            result.rateCards || []
          );
          const collectedRevenue = Number(result.dailyPaidRevenue || 0);
          const closedRevenue = Number(result.dailyRevenue || 0);
          const totalRevenue = Math.max(closedRevenue, collectedRevenue) + currentRevenue;

          return {
            parlourId: parlour.id,
            parlourName: parlour.name,
            sport,
            totalAreas,
            runningCount,
            availableCount: Math.max(0, totalAreas - runningCount),
            unsettledCount,
            currentRevenue,
            collectedRevenue,
            totalRevenue,
            route: this.resolveRouteForSport(sport)
          };
        }).sort((left, right) => left.sport.localeCompare(right.sport));
        this.isLoadingSnapshots = false;
      },
      error: () => {
        this.sportSnapshots = [];
        this.isLoadingSnapshots = false;
      }
    });
  }

  private buildBannerSubtitle(parlours: { name: string }[], sports: string[]): string {
    if (parlours.length > 1) {
      return `${parlours.length} registered venues across ${sports.length} sport${sports.length > 1 ? 's' : ''}, with live snapshots ready below.`;
    }

    if (sports.length === 1 && sports[0] === 'BADMINTON') {
      return 'Your badminton academy dashboard starts here, with live snapshots and quick links.';
    }

    if (sports.length === 1 && sports[0] === 'SNOOKER') {
      return 'Your snooker parlour dashboard starts here, with live snapshots and quick links.';
    }

    return 'Manage snooker tables and badminton courts from one smooth sports workspace.';
  }

  private resolvePreferredParlour(parlours: { name: string; sport?: string }[]) {
    if (!parlours.length) {
      return undefined;
    }

    if (parlours.length > 1) {
      const badmintonParlour = parlours.find(
        parlour => (parlour.sport || 'SNOOKER').trim().toUpperCase() === 'BADMINTON'
      );
      if (badmintonParlour) {
        return badmintonParlour;
      }
    }

    const activeSport = this.authService.activeSportValue;
    return parlours.find(
      parlour => (parlour.sport || 'SNOOKER').trim().toUpperCase() === activeSport
    ) || parlours[0];
  }

  private resolveRouteForSport(sport: string): string {
    const normalizedSport = this.normalizeSport(sport);
    if (normalizedSport === 'BADMINTON') {
      return '/badminton';
    }
    if (normalizedSport === 'SNOOKER') {
      return '/dashboard';
    }
    return '/manage-tables';
  }

  private normalizeSport(sport?: string | null): string {
    return (sport || 'SNOOKER').trim().toUpperCase();
  }

  private calculateCurrentRevenue(sport: string, tables: any[], activeSessions: any[], rateCards: any[]): number {
    const now = new Date().getTime();
    const activeRateCards = (rateCards || []).filter(rateCard => rateCard?.isActive);
    const rateByGameType = new Map(
      activeRateCards.map(rateCard => [rateCard.gameType, Number(rateCard.ratePerHour || 0)])
    );
    const normalizedSport = this.normalizeSport(sport);

    return (activeSessions || []).reduce((sum, session) => {
      const startedAt = this.parseDate(session?.startedAt);
      if (!startedAt) {
        return sum;
      }

      const table = (tables || []).find(item => item?.id === session?.gameTableId);
      const hourlyRate = Number(
        table?.hourlyRate
        || rateByGameType.get(table?.gameType)
        || 0
      );

      if (hourlyRate <= 0) {
        return sum;
      }

      const elapsedMs = Math.max(0, now - startedAt.getTime());
      const currentCost = Number(session?.currentCost || 0) || ((elapsedMs / 3600000) * hourlyRate);
      if (normalizedSport === 'BADMINTON') {
        const paymentMode = String(session?.paymentMode || session?.notes || '').toUpperCase();
        const paidAmount = Number(session?.paidAmount || 0);
        if (paymentMode.includes('PAID')) {
          return sum;
        }
        return sum + Math.max(currentCost - paidAmount, 0);
      }

      return sum + currentCost;
    }, 0);
  }

  private parseDate(dateValue: any): Date | undefined {
    if (!dateValue) return undefined;
    if (Array.isArray(dateValue) && dateValue.length >= 6) {
      return new Date(dateValue[0], dateValue[1] - 1, dateValue[2], dateValue[3], dateValue[4], dateValue[5]);
    }
    const parsed = new Date(dateValue);
    return Number.isNaN(parsed.getTime()) ? undefined : parsed;
  }
}
