import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, OnInit, inject } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';

interface AdminCustomer {
  id: number;
  organizationName: string;
  primaryContactName?: string;
  primaryContactEmail?: string;
  primaryContactPhone?: string;
  sports: string[];
  subscriptionType: string;
  billingAmount: number;
  billingCurrency: string;
  trialDurationDays?: number;
  trialStartedAt?: string;
  trialEndsAtDateTime?: string;
  nextBillingDate?: string;
  customerStatus?: string;
  invitationStatus?: string;
  subscriptionStatus?: string;
  paymentStatus?: string;
  accessStatus?: string;
  graceStatus?: string;
  graceEndsAt?: string;
  onboardingCode?: string;
  invitationId?: number;
  onboardingCodeExpiresAt?: string;
  notes?: string;
}

interface AdminDashboard {
  totalCustomers: number;
  activeCustomers: number;
  pendingRegistration: number;
  accessBlocked: number;
  paymentDue: number;
  monthlyRecurringRevenue: number;
}

@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, FormsModule],
  templateUrl: './admin-dashboard.component.html',
  styleUrl: './admin-dashboard.component.scss'
})
export class AdminDashboardComponent implements OnInit {
  private http = inject(HttpClient);
  private fb = inject(FormBuilder);

  dashboard?: AdminDashboard;
  customers: AdminCustomer[] = [];
  isLoading = false;
  isCreating = false;
  message = '';
  isSuccess = false;
  actionReason = '';
  graceEndsAt = '';
  paymentAmount = 0;
  paymentReference = '';
  activeActionCustomer: AdminCustomer | null = null;
  activeAction: 'grace' | 'payment' | 'suspend' | 'cancel' | 'reactivate' | null = null;

  planOptions = [
    { value: 'SINGLE', label: 'Single Sport' },
    { value: 'DUAL', label: 'Dual Sports' },
    { value: 'MULTI_SPORTS', label: 'Multi Sports' },
    { value: 'ACADEMY', label: 'Academy' }
  ];

  invitationForm = this.fb.group({
    customerName: ['', [Validators.required, Validators.pattern(/\S/)]],
    ownerName: ['', [Validators.required, Validators.pattern(/\S/)]],
    ownerEmail: ['', [Validators.required, Validators.email]],
    ownerPhone: ['', [Validators.required, Validators.pattern(/\S/)]],
    customerPlan: ['SINGLE', Validators.required],
    trialDays: [7, [Validators.required, Validators.min(0)]],
    invitationExpiryDays: [7, [Validators.required, Validators.min(1)]],
    subscriptionType: ['MONTHLY', Validators.required],
    billingAmount: [0, [Validators.required, Validators.min(0)]],
    notes: ['']
  });

  ngOnInit(): void {
    this.loadAdminData();
  }

  createInvitation(): void {
    if (this.invitationForm.invalid || this.isCreating) {
      this.invitationForm.markAllAsTouched();
      this.showMessage('Complete all mandatory invitation fields.', false);
      return;
    }

    const value = this.invitationForm.getRawValue();
    this.isCreating = true;
    this.http.post<AdminCustomer>('/api/admin/customers/invitations', {
      customerName: value.customerName?.trim(),
      ownerName: value.ownerName?.trim(),
      ownerEmail: value.ownerEmail?.trim(),
      ownerPhone: value.ownerPhone?.trim(),
      sports: [value.customerPlan || 'SINGLE'],
      trialDays: value.trialDays,
      invitationExpiryDays: value.invitationExpiryDays,
      subscriptionType: value.subscriptionType,
      billingAmount: value.billingAmount,
      billingCurrency: 'INR',
      notes: value.notes
    }).subscribe({
      next: customer => {
        this.showMessage(`Invitation created. Activation code: ${customer.onboardingCode}`, true);
        this.isCreating = false;
        this.invitationForm.reset({
          customerPlan: 'SINGLE',
          trialDays: 7,
          invitationExpiryDays: 7,
          subscriptionType: 'MONTHLY',
          billingAmount: 0
        });
        this.loadAdminData();
      },
      error: error => {
        this.isCreating = false;
        this.showMessage(error.error?.message || 'Could not create invitation.', false);
      }
    });
  }

  loadAdminData(): void {
    this.isLoading = true;
    this.http.get<AdminDashboard>('/api/admin/dashboard').subscribe({
      next: dashboard => {
        this.dashboard = dashboard;
        this.http.get<AdminCustomer[]>('/api/admin/customers').subscribe({
          next: customers => {
            this.customers = customers || [];
            this.isLoading = false;
          },
          error: error => {
            this.customers = [];
            this.isLoading = false;
            this.showMessage(error.error?.message || 'Could not load customers.', false);
          }
        });
      },
      error: error => {
        this.isLoading = false;
        this.showMessage(error.error?.message || 'Admin dashboard failed to load.', false);
      }
    });
  }

  resendInvitation(customer: AdminCustomer): void {
    this.http.post<AdminCustomer>(`/api/admin/customers/${customer.id}/invitations/resend`, {}).subscribe({
      next: updated => {
        this.showMessage(`Invitation resent. New activation code: ${updated.onboardingCode}`, true);
        this.loadAdminData();
      },
      error: error => this.showMessage(error.error?.message || 'Could not resend invitation.', false)
    });
  }

  revokeInvitation(customer: AdminCustomer): void {
    if (!customer.invitationId) return;
    this.http.post<AdminCustomer>(`/api/admin/invitations/${customer.invitationId}/revoke`, {
      reason: 'Revoked from admin dashboard'
    }).subscribe({
      next: () => {
        this.showMessage('Invitation revoked.', true);
        this.loadAdminData();
      },
      error: error => this.showMessage(error.error?.message || 'Could not revoke invitation.', false)
    });
  }

  openAction(customer: AdminCustomer, action: 'grace' | 'payment' | 'suspend' | 'cancel' | 'reactivate'): void {
    this.activeActionCustomer = customer;
    this.activeAction = action;
    this.actionReason = '';
    this.paymentAmount = customer.billingAmount || 0;
    this.paymentReference = '';
    this.graceEndsAt = this.dateTimeLocal(new Date(Date.now() + 3 * 24 * 60 * 60 * 1000));
  }

  closeAction(): void {
    this.activeActionCustomer = null;
    this.activeAction = null;
  }

  confirmAction(): void {
    const customer = this.activeActionCustomer;
    if (!customer || !this.activeAction) return;

    const endpoint = `/api/admin/customers/${customer.id}`;
    let request = this.http.post<AdminCustomer>(`${endpoint}/${this.activeAction}`, { reason: this.actionReason });
    if (this.activeAction === 'grace') {
      request = this.http.post<AdminCustomer>(`${endpoint}/grace-period`, {
        endsAt: new Date(this.graceEndsAt).toISOString().slice(0, 19),
        reason: this.actionReason
      });
    }
    if (this.activeAction === 'payment') {
      request = this.http.post<AdminCustomer>(`${endpoint}/payments`, {
        amount: this.paymentAmount,
        currency: customer.billingCurrency || 'INR',
        paymentReference: this.paymentReference,
        notes: this.actionReason
      });
    }

    request.subscribe({
      next: () => {
        this.showMessage('Customer lifecycle updated.', true);
        this.closeAction();
        this.loadAdminData();
      },
      error: error => this.showMessage(error.error?.message || 'Could not update customer.', false)
    });
  }

  canResend(customer: AdminCustomer): boolean {
    return customer.invitationStatus === 'CREATED' || customer.invitationStatus === 'SENT' || customer.invitationStatus === 'EXPIRED';
  }

  canRevoke(customer: AdminCustomer): boolean {
    return !!customer.invitationId && (customer.invitationStatus === 'CREATED' || customer.invitationStatus === 'SENT' || customer.invitationStatus === 'EXPIRED');
  }

  canGrantGrace(customer: AdminCustomer): boolean {
    return customer.customerStatus === 'ACTIVE' && customer.subscriptionStatus === 'PAYMENT_DUE' && !customer.graceStatus;
  }

  canRecordPayment(customer: AdminCustomer): boolean {
    return customer.customerStatus !== 'INACTIVE' && customer.subscriptionStatus !== 'CANCELLED';
  }

  canSuspend(customer: AdminCustomer): boolean {
    return customer.customerStatus === 'ACTIVE';
  }

  canReactivate(customer: AdminCustomer): boolean {
    return customer.customerStatus === 'INACTIVE' || customer.customerStatus === 'SUSPENDED';
  }

  planLabel(customer: AdminCustomer): string {
    const plan = customer.sports?.[0] || 'SINGLE';
    return this.planOptions.find(option => option.value === plan)?.label || plan;
  }

  formatDate(value?: string): string {
    return value ? new Date(value).toLocaleString() : '-';
  }

  private dateTimeLocal(date: Date): string {
    const offset = date.getTimezoneOffset() * 60000;
    return new Date(date.getTime() - offset).toISOString().slice(0, 16);
  }

  private showMessage(message: string, success: boolean): void {
    this.message = message;
    this.isSuccess = success;
  }
}
