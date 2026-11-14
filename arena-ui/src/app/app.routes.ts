import {MVP_FEATURES} from './features';
import {PublicClubComponent} from './checkin/public-club.component';
import {ClubManagementComponent} from './checkin/club-management.component';
import {FacilityQrComponent} from './checkin/facility-qr.component';
import {clubHostname} from './checkin/public-club.service';
import {PublicCheckinComponent} from './checkin/public-checkin.component';
import {financialReportsGuard} from './services/workspace-guards';
import {setupManagementGuard,businessProfileGuard} from './services/workspace-guards';
import { FacilityResourcesComponent } from './facilities/facility-resources.component';
import { SportsActivitiesComponent } from './training/sports-activities.component';
import { SportConfigurationComponent } from './training/sport-configuration.component';
import { inject } from '@angular/core';
import { Router, Routes } from '@angular/router';
import { HomeComponent } from './home/home.component';
import { LoginComponent } from './login/login.component';
import { AuthCallbackComponent } from './auth-callback/auth-callback.component';
import { UserRegistrationComponent } from './user-registration/user-registration.component';
import { AdminDashboardComponent } from './admin/admin-dashboard.component';
import { AcademyComponent } from './academy/academy.component';
import { TrainingComponent } from './training/training.component';
import { BillingComponent } from './billing/billing.component';
import { BusinessProfileComponent } from './business-profile/business-profile.component';
import { StudentsComponent } from './students/students.component';
import { AcademyOperationsComponent } from './students/academy-operations.component';
import { ReportsComponent } from './sessions/reports.component';
import { SessionHistoryComponent } from './sessions/session-history.component';
import { RunningDashboardComponent } from './sessions/running-dashboard.component';
import { AuthService } from './services/auth';

const authGuard = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.isAuthenticated() ? true : router.createUrlTree(['/login']);
};

const adminGuard = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.hasRole('ADMIN') ? true : router.createUrlTree(['/']);
};

export const routes: Routes = [
  {path:'public/:slug/check-in',component:PublicCheckinComponent},
  {path:'public/:slug',component:PublicClubComponent},
  {path:'academy/public-site/preview',component:PublicClubComponent,data:{ownerPreview:true},canActivate:[authGuard,()=>MVP_FEATURES.publicClubWebsite&&inject(AuthService).hasRole('OWNER')?true:inject(Router).createUrlTree(['/academy/dashboard'])]},
  {path:'check-in',component:PublicCheckinComponent},
  {path:'academy/business-profile',redirectTo:'business-profile',pathMatch:'full'},
  {path:'business-profile',component:BusinessProfileComponent,canActivate:[authGuard,businessProfileGuard],canDeactivate:[(component:BusinessProfileComponent)=>component.canLeave()]},
  {path:'',component:PublicClubComponent,canMatch:[()=>clubHostname()]},
  { path: '', component: HomeComponent },
  {path:'academy/public-site',component:ClubManagementComponent,data:{clubMode:'site'},canActivate:[authGuard,()=>MVP_FEATURES.publicClubWebsite&&inject(AuthService).hasRole('OWNER')?true:inject(Router).createUrlTree(['/academy/dashboard'])]},
  {path:'academy/customers',component:ClubManagementComponent,data:{clubMode:'customers'},canActivate:[authGuard,setupManagementGuard]},
  {path:'academy/public-reservations',component:ClubManagementComponent,data:{clubMode:'bookings'},canActivate:[authGuard,()=>MVP_FEATURES.publicReservationRequests?true:inject(Router).createUrlTree(['/academy/dashboard']),setupManagementGuard]},
  {path:'academy/facility-qr/:id',component:FacilityQrComponent,canActivate:[authGuard,setupManagementGuard]},
  { path: 'login/callback', component: AuthCallbackComponent },
  { path: 'login', component: LoginComponent, pathMatch: 'full' },
  { path: 'register', component: UserRegistrationComponent },
  { path: 'admin', component: AdminDashboardComponent, canActivate: [authGuard, adminGuard] },
  { path: 'billing', component: BillingComponent, canActivate: [authGuard, () => inject(AuthService).hasRole('OWNER') ? true : inject(Router).createUrlTree(['/'])] },
  {path:'academy/sports',component:SportsActivitiesComponent,canActivate:[authGuard,setupManagementGuard]},
  {path:'academy/sports/:id/configure',component:SportConfigurationComponent,canActivate:[authGuard,setupManagementGuard],canDeactivate:[(c:SportConfigurationComponent)=>c.canLeave()]},
  ...['staff','batches'].map(section => ({path: `academy/${section}`, component: TrainingComponent, data:{section}, canActivate:[authGuard, setupManagementGuard]})),
  {path:'academy/students',component:StudentsComponent,canActivate:[authGuard,setupManagementGuard]},
  ...['attendance','collections'].map(section => ({path:`academy/${section}`,component:AcademyOperationsComponent,data:{section},canActivate:[authGuard]})),
  {path:'academy/reports',component:ReportsComponent,canActivate:[authGuard,financialReportsGuard]},
  {path:'academy/history',component:SessionHistoryComponent,canActivate:[authGuard]},
  {path:'academy/dashboard',component:RunningDashboardComponent,canActivate:[authGuard]},
  {path:'academy/setup',component:AcademyComponent,data:{page:'setup'},canActivate:[authGuard,setupManagementGuard]},
  {path:'academy/facilities',component:FacilityResourcesComponent,runGuardsAndResolvers:'paramsOrQueryParamsChange',canActivate:[authGuard,setupManagementGuard],canDeactivate:[(c:FacilityResourcesComponent)=>c.canLeave()]},
  ...['setup'].map(page=>({path:`academy/${page}`,component:AcademyComponent,data:{page},canActivate:[authGuard,setupManagementGuard]})),
  { path: '**', redirectTo: '' }
];
