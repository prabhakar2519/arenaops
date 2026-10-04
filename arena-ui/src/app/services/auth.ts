import { environment } from '../../environments/environment';
import { authorizationUrl, consumeVerifier } from './oidc';
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, defer } from 'rxjs';
import { tap, timeout } from 'rxjs/operators';

export type UserRole = 'ADMIN' | 'OWNER' | 'STAFF' | null;

export interface User {
  username?: string;
  name?: string;
  role?: UserRole;
  error?: any;
}

export interface Parlour {
  id: number;
  name: string;
  sport?: string;
  ownerName?: string;
  phone?: string;
  email?: string;
  city?: string;
  state?: string;
  publicSlug?: string;
  bookingUrl?: string;
}

export interface SportRegistrationOptions {
  entitledSports: string[];
  registeredSports: string[];
  availableSports: string[];
}

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private static readonly RETURN_TO_LOGIN_KEY = 'arenaops.returnToLogin';
  private static readonly ACCESS_DENIED_MESSAGE_KEY = 'arenaops.accessDeniedMessage';
  private static readonly FORCE_FRESH_LOGIN_KEY = 'arenaops.forceFreshLogin';
  private http = inject(HttpClient);
  private currentUserSubject = new BehaviorSubject<User | null>(null);
  public currentUser$ = this.currentUserSubject.asObservable();

  private parlourNameSubject = new BehaviorSubject<string | null>(null);
  public parlourName$ = this.parlourNameSubject.asObservable();

  private parloursSubject = new BehaviorSubject<Parlour[]>([]);
  public parlours$ = this.parloursSubject.asObservable();

  private registrationOptionsSubject = new BehaviorSubject<SportRegistrationOptions | null>(null);
  public registrationOptions$ = this.registrationOptionsSubject.asObservable();

  private activeSportSubject = new BehaviorSubject<string>('SNOOKER');
  public activeSport$ = this.activeSportSubject.asObservable();

  constructor() {
    // Check local storage for existing session info (profile only)
    const savedUser = localStorage.getItem('currentUser');
    if (savedUser) {
      this.currentUserSubject.next(JSON.parse(savedUser));
    }
    const savedParlourName = localStorage.getItem('parlourName');
    if (savedParlourName) {
      this.parlourNameSubject.next(savedParlourName);
    }
    const savedParlours = localStorage.getItem('parlours');
    if (savedParlours) {
      this.parloursSubject.next(JSON.parse(savedParlours));
    }
    const savedActiveSport = localStorage.getItem('activeSport');
    if (savedActiveSport) {
      this.activeSportSubject.next(savedActiveSport);
    }
  }

  setParlourName(name: string): void {
    this.parlourNameSubject.next(name);
    localStorage.setItem('parlourName', name);
  }

  setParlours(parlours: Parlour[]): void {
    this.parloursSubject.next(parlours);
    localStorage.setItem('parlours', JSON.stringify(parlours));
  }

  fetchParlours(): Observable<Parlour[]> {
    return this.http.get<Parlour[]>('/api/parlours').pipe(
      tap(parlours => this.setParlours(parlours || []))
    );
  }

  fetchRegistrationOptions(): Observable<SportRegistrationOptions> {
    return this.http.get<SportRegistrationOptions>('/api/parlours/registration-options').pipe(
      tap(options => this.registrationOptionsSubject.next(options))
    );
  }

  get registrationOptionsValue(): SportRegistrationOptions | null {
    return this.registrationOptionsSubject.value;
  }

  get canRegisterAnotherSport(): boolean {
    return (this.registrationOptionsValue?.availableSports?.length || 0) > 0;
  }

  setActiveSport(sport: string): void {
    const normalizedSport = this.normalizeSport(sport);
    this.activeSportSubject.next(normalizedSport);
    localStorage.setItem('activeSport', normalizedSport);
  }

  get activeSportValue(): string {
    return this.activeSportSubject.value;
  }

  get parloursValue(): Parlour[] {
    return this.parloursSubject.value;
  }

  getParlourForSport(sport: string): Parlour | undefined {
    const normalizedSport = this.normalizeSport(sport);
    return this.parloursValue.find(parlour => this.normalizeSport(parlour.sport) === normalizedSport);
  }

  get availableSports(): string[] {
    const registeredSports = this.parloursValue
        .map(parlour => this.normalizeSport(parlour.sport))
        .filter(Boolean);
    const entitledSports = this.hasRole('OWNER')
      ? (this.registrationOptionsValue?.entitledSports || []).map(sport => this.normalizeSport(sport))
      : [];
    return [...new Set([...registeredSports, ...entitledSports])];
  }

  async loginWithKeycloak(): Promise<void> {
    const url = new URL(await authorizationUrl(environment, window.location.origin, sessionStorage));
    if (sessionStorage.getItem(AuthService.FORCE_FRESH_LOGIN_KEY) === 'true') {
      sessionStorage.removeItem(AuthService.FORCE_FRESH_LOGIN_KEY);
      url.searchParams.set('prompt', 'login');
      url.searchParams.set('max_age', '0');
    }
    window.location.href = url.toString();
  }

  checkAvailability(): Observable<unknown> {
    return this.http.get('/api/readiness').pipe(timeout(10000));
  }

  async resetPasswordWithKeycloak(): Promise<void> {
    window.location.href = await authorizationUrl(environment, window.location.origin, sessionStorage, crypto, true);
  }

  validateSession(): Observable<User> {
    // Call backend to validate session and get user info
    // The backend (BFF) will check its own session
    return this.http.get<User>('/api/user').pipe(
      timeout(20000),
      tap({
        next: user => {
          localStorage.setItem('currentUser', JSON.stringify(user));
          this.currentUserSubject.next(user);
        },
        error: () => {
          localStorage.removeItem('currentUser');
          this.currentUserSubject.next(null);
        }
      })
    );
  }

  exchangeCodeForToken(code: string, state?: string): Observable<User> {
    const redirectUri = `${window.location.origin}/login/callback`;

    // Exchange the authorization code for a session on the backend
    // The backend (BFF) stores the tokens and returns only user info
    return defer(() => {
      const codeVerifier = consumeVerifier(state, sessionStorage);
      return this.http.post<User>('/api/token', { code, redirectUri, codeVerifier });
    }).pipe(
      timeout(20000),
      tap({
        next: user => {
          console.log('User info received from BFF:', user);

          // Store user profile in localStorage for UI purposes
          localStorage.setItem('currentUser', JSON.stringify(user));
          this.currentUserSubject.next(user);
        },
        error: () => {
          localStorage.removeItem('currentUser');
          this.currentUserSubject.next(null);
        }
      })
    );
  }


  async logout(): Promise<void> {
    // Finish clearing the BFF session before leaving the application.
    try {
      const response = await fetch('/api/logout', { method: 'POST', credentials: 'include', keepalive: true,
        signal: AbortSignal.timeout(10000) });
      if (!response.ok) console.warn('Local logout returned status', response.status);
    } catch {
      console.warn('Local logout could not complete; continuing to Keycloak logout.');
    }
    // Clear all stored authentication data (profile info)
    localStorage.removeItem('currentUser');
    localStorage.removeItem('parlourName');
    localStorage.removeItem('parlours');
    localStorage.removeItem('activeSport');
    this.currentUserSubject.next(null);
    this.parlourNameSubject.next(null);
    this.parloursSubject.next([]);
    this.activeSportSubject.next('SNOOKER');
    this.registrationOptionsSubject.next(null);

    // Clear all storage
    localStorage.clear();
    sessionStorage.clear();

    // Logout from Keycloak and redirect back to home page
    const baseUrl = window.location.origin;
    const keycloakLogoutUrl = `${environment.keycloakBaseUrl}/realms/${environment.keycloakRealm}/protocol/openid-connect/logout`;
    const redirectUri = encodeURIComponent(`${baseUrl}/`);

    window.location.href = `${keycloakLogoutUrl}?client_id=arena-ui&post_logout_redirect_uri=${redirectUri}`;
  }

  logoutAfterAccessDenied(message: string): void {
    // Clear the BFF session, but keep the user on the callback page so the
    // actual access error is visible. Redirecting to Keycloak's end-session
    // endpoint without an id_token_hint opens its logout confirmation page
    // and makes a rejected login look like an unexpected logout.
    fetch('/api/logout', { method: 'POST', credentials: 'include', keepalive: true }).catch(() => undefined);
    localStorage.clear();
    sessionStorage.clear();
    sessionStorage.setItem(AuthService.ACCESS_DENIED_MESSAGE_KEY, message);
    sessionStorage.setItem(AuthService.FORCE_FRESH_LOGIN_KEY, 'true');
    this.currentUserSubject.next(null);
    this.parlourNameSubject.next(null);
    this.parloursSubject.next([]);
    this.activeSportSubject.next('SNOOKER');
  }

  consumeAccessDeniedMessage(): string | null {
    const message = sessionStorage.getItem(AuthService.ACCESS_DENIED_MESSAGE_KEY);
    sessionStorage.removeItem(AuthService.ACCESS_DENIED_MESSAGE_KEY);
    return message;
  }

  consumeReturnToLogin(): boolean {
    const shouldReturn = sessionStorage.getItem(AuthService.RETURN_TO_LOGIN_KEY) === 'true';
    if (shouldReturn) {
      sessionStorage.removeItem(AuthService.RETURN_TO_LOGIN_KEY);
    }
    return shouldReturn;
  }

  get currentUserValue(): User | null {
    return this.currentUserSubject.value;
  }

  hasRole(role: UserRole): boolean {
    return this.currentUserValue?.role === role;
  }

  isAuthenticated(): boolean {
    return !!this.currentUserValue;
  }

  private normalizeSport(sport?: string | null): string {
    return (sport || 'SNOOKER').trim().toUpperCase();
  }
}
