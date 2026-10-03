import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { Observable, finalize, switchMap, tap } from 'rxjs';
import { billingRequestId, BillingService, BillingOverview, BillingOrder, BillingPlan, PaymentOutcome } from './billing.service';
import { apiError } from '../api-error';

/** Presents authoritative billing state and restores checkout using the order URL. */
@Component({
  selector: 'app-billing', standalone: true, imports: [CommonModule],
  templateUrl: './billing.component.html', styleUrl: './billing.component.scss'
})
export class BillingComponent implements OnInit {
  private api = inject(BillingService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  overview: BillingOverview | null = null;
  order: BillingOrder | null = null;
  busy = false;
  error = '';
  private selectionKey = '';
  private requestId = '';

  /** Fetches server state before exposing any payment actions. */
  ngOnInit(): void { this.reload(); }
  /** Restores an explicit URL order, otherwise the server's unfinished order. */
  reload(): void {
    this.run(this.api.overview().pipe(tap(v => this.overview = v), switchMap(v => {
      const id = this.route.snapshot.queryParamMap.get('order');
      return id ? this.api.order(id) : new Observable<BillingOrder | null>(subscriber => {
        subscriber.next(v.pendingOrder); subscriber.complete();
      });
    })), order => this.order = order);
  }
  /** Creates a selection once; a network retry uses the same request identifier. */
  select(plan: BillingPlan): void {
    if (this.busy) return;
    const key = `${plan.id}:${plan.cycle}`;
    if (key !== this.selectionKey) { this.selectionKey = key; this.requestId = billingRequestId(); }
    this.run(this.api.create(plan.id, plan.cycle, this.requestId), o => this.show(o));
  }
  /** Initiates checkout or creates a fresh attempt after failure. */
  checkout(): void { if (this.order) this.run(this.api.checkout(this.order.id), o => this.show(o)); }
  /** Applies a simulated result, then reloads entitlement from the backend. */
  simulate(outcome: PaymentOutcome): void {
    if (!this.order || !this.overview?.mockEnabled || !this.order.attemptId) return;
    this.run(this.api.simulate(this.order, outcome).pipe(tap(o => this.show(o)), switchMap(() => this.api.overview())),
      v => this.overview = v);
  }
  /** Abandons a selection safely so another cycle may be chosen. */
  back(): void {
    if (!this.order) return;
    this.run(this.api.cancel(this.order.id), () => this.clear());
  }
  /** Returns to plan selection after a terminal order without trusting cached entitlement. */
  clear(): void {
    this.order = null; this.selectionKey = ''; this.requestId = '';
    void this.router.navigate([], { queryParams: {}, replaceUrl: true });
  }
  /** Records the order in the URL so refresh resumes persisted payment state. */
  private show(order: BillingOrder): void {
    this.order = order;
    void this.router.navigate([], { queryParams: { order: order.id }, replaceUrl: true });
  }
  /** Prevents duplicate clicks and displays the shared sanitized error contract. */
  private run<T>(operation: Observable<T>, success: (value: T) => void): void {
    if (this.busy) return;
    this.busy = true; this.error = '';
    operation.pipe(finalize(() => this.busy = false)).subscribe({ next: success, error: e => this.error = apiError(e).description });
  }
  /** Disables payment actions when the persisted order expiry has elapsed. */
  get expired(): boolean { return !!this.order && new Date(this.order.expiresAt).getTime() <= Date.now(); }
}
