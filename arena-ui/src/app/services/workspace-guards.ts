import {RunningSessionsService} from '../sessions/running-sessions.service';
import {inject} from '@angular/core';
import {Router} from '@angular/router';
import {map,catchError,of} from 'rxjs';
import {AuthService} from './auth';

export const setupManagementGuard=()=>{
 const auth=inject(AuthService),router=inject(Router);
 return auth.hasRole('OWNER')||auth.hasRole('MANAGER')?true:router.createUrlTree(['/academy/dashboard']);
};
export const businessProfileGuard=()=>{
 const auth=inject(AuthService),router=inject(Router);
 if(!auth.hasRole('OWNER')&&!auth.hasRole('MANAGER'))return of(router.createUrlTree(['/academy/dashboard']));
 return auth.fetchBusinessBranding().pipe(map(b=>b.permissions.viewProfile?true:router.createUrlTree(['/academy/dashboard'])),catchError(()=>of(router.createUrlTree(['/academy/dashboard']))));
};

export const financialReportsGuard=()=>{const service=inject(RunningSessionsService),router=inject(Router);return service.authority().pipe(map(p=>{service.permissions.set(p);return p.canViewFinancials?true:router.createUrlTree(['/academy/dashboard']);}),catchError(()=>of(router.createUrlTree(['/academy/dashboard']))));};
