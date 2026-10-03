import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';

export type BillingCycle = 'MONTHLY' | 'ANNUAL';
export type PaymentOutcome = 'PAID' | 'FAILED' | 'CANCELLED';
export interface BillingPlan {
  id: string; name: string; cycle: BillingCycle; baseAmount: number;
  taxAmount: number; total: number; currency: string;
}
export interface BillingOrder {
  id: string; academy: string; planId: string; planName: string; cycle: BillingCycle;
  baseAmount: number; taxAmount: number; total: number; currency: string;
  status: 'CREATED' | 'PAYMENT_PENDING' | PaymentOutcome;
  expiresAt: string; attemptId: string | null; paymentStatus: string | null;
}
export interface BillingOverview {
  academy: string; status: string; currentPlan: string; sportsEntitlement: string; currentPrice: number; currentCurrency: string; billingCycle: string; paymentStatus: string;
  trialStartedAt: string | null; trialEndsAt: string | null; remainingTrialDays: number;
  subscriptionStartedAt: string | null; renewalAt: string | null; mockEnabled: boolean;
  plans: BillingPlan[]; pendingOrder: BillingOrder | null;
}

/** Generates a request UUID on localhost and on development LAN origins without randomUUID support. */
export function billingRequestId(source: Crypto = globalThis.crypto): string {
  if (typeof source.randomUUID === 'function') return source.randomUUID();
  const bytes = source.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = Array.from(bytes, byte => byte.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

/** Calls the authenticated BFF; request contracts intentionally contain no tenant or monetary fields. */
@Injectable({ providedIn: 'root' })
export class BillingService {
  private http = inject(HttpClient);
  /** Loads authoritative entitlement, prices and unfinished checkout. */
  overview() { return this.http.get<BillingOverview>('/api/billing'); }
  /** Persists a selection using a request identifier retained across network retries. */
  create(planId: string, cycle: BillingCycle, requestId: string) {
    return this.http.post<BillingOrder>('/api/billing/orders', { planId, cycle, requestId });
  }
  /** Restores an order from its URL after browser refresh. */
  order(id: string) { return this.http.get<BillingOrder>(`/api/billing/orders/${encodeURIComponent(id)}`); }
  /** Starts or resumes a provider attempt. */
  checkout(id: string) { return this.http.post<BillingOrder>(`/api/billing/orders/${encodeURIComponent(id)}/checkout`, {}); }
  /** Cancels a selection that has not started checkout. */
  cancel(id: string) { return this.http.post<BillingOrder>(`/api/billing/orders/${encodeURIComponent(id)}/cancel`, {}); }
  /** Sends a simulator outcome without accepting payment credentials or amounts. */
  simulate(order: BillingOrder, outcome: PaymentOutcome) {
    return this.http.post<BillingOrder>(`/api/billing/orders/${encodeURIComponent(order.id)}/mock`, { attemptId: order.attemptId, outcome });
  }
}
