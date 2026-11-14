import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {RunningSessionsService} from '../sessions/running-sessions.service';
import { Component, inject, OnInit, DestroyRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../services/auth';

@Component({selector:'ao-sidebar',standalone:true,imports:[CommonModule,RouterLink,RouterLinkActive],styleUrl:'./sidebar-structure.scss',template:`
 <button class="mobile-nav-toggle" (click)="open=!open" [attr.aria-expanded]="open" aria-controls="workspace-navigation">☰ <span>Workspace menu</span></button>
 <button *ngIf="open" class="navigation-backdrop" aria-label="Close navigation" (click)="open=false"></button>
 <aside class="workspace-sidebar" [class.open]="open" (keydown.escape)="open=false">
 <div class="sidebar-caption">Your workspace</div>
 <nav id="workspace-navigation" aria-label="Workspace navigation">
 <details class="dashboard-navigation" open><summary class="primary-heading"><span class="dashboard-heading-icon" aria-hidden="true">▦</span>Dashboard</summary>
 <a [attr.href]="dashboardTarget" [class.active]="dashboardIsActive" [attr.aria-current]="dashboardIsActive?'page':null" (click)="navigateDashboard($event);open=false">Overview</a>
 <a *ngIf="!auth.hasRole('ADMIN')" routerLink="/academy/history" routerLinkActive="active" (click)="open=false">Session History</a>
 <a *ngIf="sessions.permissions()?.canViewFinancials" routerLink="/academy/reports" routerLinkActive="active" (click)="open=false">Reports</a></details>
 <a class="primary-heading" *ngIf="canManageStaff" routerLink="/academy/customers" routerLinkActive="active" (click)="open=false"><span aria-hidden="true"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6"><circle cx="9" cy="8" r="3"/><path d="M3 21v-3a6 6 0 0 1 12 0v3M16 5a3 3 0 0 1 0 6M21 21v-3a6 6 0 0 0-4-5"/></svg></span>Customers</a>
 <details *ngIf="canManage || canViewProfile || canManageStaff" open>
 <summary class="primary-heading"><span class="heading-space" aria-hidden="true">⚙</span><span class="heading-label">Setup &amp; Management</span></summary>
 <a *ngIf="canViewProfile" routerLink="/business-profile" routerLinkActive="active" (click)="open=false"><span aria-hidden="true">◇</span>Business Profile</a>
 <a *ngIf="canManageStaff" routerLink="/academy/sports" routerLinkActive="active" (click)="open=false"><span aria-hidden="true">◈</span>Sports &amp; Activities</a>
 <a *ngIf="canManageStaff" routerLink="/academy/staff" routerLinkActive="active" (click)="open=false"><span aria-hidden="true">♧</span>Staff &amp; Coaches</a>
 <a *ngIf="canManageStaff" routerLink="/academy/facilities" routerLinkActive="active" (click)="open=false"><span aria-hidden="true">◇</span>Facilities &amp; Resources</a>
 </details>
 <details *ngIf="canManage" open>
 <summary class="primary-heading"><span class="heading-space" aria-hidden="true"></span>Academy Operations</summary>
 <a routerLink="/academy/students" routerLinkActive="active" (click)="open=false"><span aria-hidden="true">♙</span>Students &amp; Members</a>
 <a routerLink="/academy/batches" routerLinkActive="active" (click)="open=false"><span aria-hidden="true">▤</span>Training &amp; Batches</a>
 <a *ngIf="canUseOperations" routerLink="/academy/attendance" routerLinkActive="active" (click)="open=false"><span aria-hidden="true">✓</span>Attendance</a>
 <a *ngIf="canManage" routerLink="/academy/collections" routerLinkActive="active" (click)="open=false"><span aria-hidden="true">¤</span>Student Collections</a>
 </details>
 <a class="primary-heading" *ngIf="auth.hasRole('OWNER')" routerLink="/billing" routerLinkActive="active" (click)="open=false"><span aria-hidden="true">▣</span>Subscription &amp; Billing</a>
 </nav>
 <footer class="sidebar-footer"><span class="support-label">Help &amp; Support</span><small>Powered by ArenaOps</small></footer>
 </aside>`})
export class SidebarComponent implements OnInit {
 readonly sessions=inject(RunningSessionsService);private destroyRef=inject(DestroyRef);
 ngOnInit():void {this.auth.currentUser$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(user=>{this.sessions.permissions.set(null);if(user&&!this.auth.hasRole('ADMIN'))this.sessions.authority().subscribe({next:p=>this.sessions.permissions.set(p),error:()=>this.sessions.permissions.set(null)});});}

 auth=inject(AuthService); private router=inject(Router); open=false;
 get canViewProfile():boolean{return this.canManageStaff && !!this.auth.businessBrandingValue?.permissions.viewProfile;}
 get canManageStaff():boolean {return this.auth.hasRole('OWNER')||this.auth.hasRole('MANAGER');}
 get canManage():boolean {return !!this.auth.academyContextValue?.eligible && (this.auth.hasRole('OWNER')||this.auth.hasRole('MANAGER'));}
 get canUseOperations():boolean {return this.canManage||this.auth.hasRole('INSTRUCTOR')||this.auth.hasRole('COACH');}
 get dashboardTarget():string {return this.auth.hasRole('ADMIN')?'/admin':'/academy/dashboard';}
 get dashboardIsActive():boolean {return this.router.isActive(this.dashboardTarget,true);}
 navigateDashboard(event:MouseEvent):void {
  event.preventDefault();
  void this.router.navigate([this.dashboardTarget]);
 }
}
