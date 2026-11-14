import {Component,Input} from '@angular/core';
import {RouterLink} from '@angular/router';

/** Shared outlined back navigation; use a stable route rather than browser history. */
@Component({selector:'ao-back-navigation',standalone:true,imports:[RouterLink],template:`<a class="btn btn-secondary" [routerLink]="target"><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="m12 5-7 7 7 7M5 12h14"/></svg>{{label}}</a>`,styles:[`:host{display:inline-block;margin-bottom:12px}a{display:inline-flex;align-items:center;gap:8px;min-height:40px;text-decoration:none}`]})
export class BackNavigationComponent {@Input() target='/academy/dashboard';@Input() label='Back to Dashboard';}
