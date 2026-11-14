import {UiFeedbackService} from '../ui/ui-feedback.service';
import {of} from 'rxjs';
import {AuthService} from '../services/auth';
import {Component,HostListener,OnInit,inject} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {ActivatedRoute,RouterLink} from '@angular/router';
import {TrainingService,AcademySport} from './training.service';
import {apiError} from '../api-error';
@Component({selector:'ao-sport-configuration',standalone:true,imports:[CommonModule,FormsModule,RouterLink],template:`
<section class="ao-workspace"><a class="btn btn-primary" routerLink="/academy/sports"><span aria-hidden="true">←</span> Back to Activities</a><h1>Configure {{sport?.name || 'activity'}}</h1><p *ngIf="loading">Loading activity…</p><p class="notice error" *ngIf="error" role="alert">{{error}}</p><button *ngIf="!loading && !sport && error" (click)="load()">Retry</button><button *ngIf="conflict" (click)="reload()">Reload latest configuration</button><section *ngIf="sport && !loading" class="panel"><div class="panel-body"><p>{{sport.active?'Active':'Inactive'}} activity</p><label class="check-label"><input type="checkbox" [(ngModel)]="training" [disabled]="busy">Training &amp; Coaching</label><p class="hint">Supports training batches and coach assignments.</p><label class="check-label"><input type="checkbox" [(ngModel)]="sessions" [disabled]="busy">Play &amp; Facility Access</label><p class="hint">Enables supported facility usage; resources are not created automatically.</p><div class="actions"><button class="btn-primary" (click)="save()" [disabled]="busy || !dirty">{{busy?'Saving…':'Save Changes'}}</button><button (click)="cancel()" [disabled]="busy || !dirty">Cancel</button></div><nav class="actions"><a *ngIf="sport.active && sport.training && training && academyManagementAvailable" [routerLink]="'/academy/batches'" [queryParams]="{sportId:sport.id}">Training Batches</a><a *ngIf="sport.active && sport.training && training" routerLink="/academy/staff">Staff &amp; Coaches</a><a *ngIf="supportedFacility && sport.active && sport.sessions && sessions" routerLink="/academy/facilities" [queryParams]="{sportId:sport.id,sportCode:sport.catalogueCode}">Manage Facilities</a></nav><p class="hint" *ngIf="!academyManagementAvailable && training">Training Batches currently require an academy workspace.</p><p class="hint" *ngIf="supportedFacility && sport.sessions">Manage Facilities stores generated resources and individual pricing.</p><p class="hint" *ngIf="!supportedFacility && sessions">A dedicated facility screen is not available for this activity.</p></div></section></section>`,styles:[`.actions{display:flex;gap:16px;flex-wrap:wrap;margin-top:24px}h1{margin:24px 0}.check-label{margin-top:20px}`]})
export class SportConfigurationComponent implements OnInit {
 feedback=inject(UiFeedbackService);
 auth=inject(AuthService);
 get academyManagementAvailable(){return !!this.auth.academyContextValue?.eligible;}
 api=inject(TrainingService);route=inject(ActivatedRoute);sport:AcademySport|null=null;training=false;sessions=false;busy=false;loading=true;error='';message='';conflict=false;
 ngOnInit(){this.load();}
 load(){this.loading=true;this.error='';this.api.get<AcademySport>(`sports/${this.route.snapshot.paramMap.get('id')}`).subscribe({next:s=>{this.sport=s;this.cancel();this.loading=false;},error:e=>{this.error=apiError(e).description;this.loading=false;}});}
 get dirty(){return !!this.sport&&(this.training!==this.sport.training||this.sessions!==this.sport.sessions);}
 get supportedFacility(){return ['SNOOKER','BADMINTON'].includes(this.sport?.catalogueCode||'');}
 cancel(){if(this.sport){this.training=this.sport.training;this.sessions=this.sport.sessions;}this.conflict=false;}
 canLeave(){return !this.busy&&(!this.dirty||this.feedback.confirm({title:'Discard activity changes?',message:'Discard unsaved activity configuration changes?',confirmText:'Discard Changes',variant:'warning'}));}
 @HostListener('window:beforeunload',['$event']) beforeUnload(e:BeforeUnloadEvent){if(this.dirty){e.preventDefault();e.returnValue='';}}
 reload(){const decision=this.canLeave();(typeof decision==='boolean'?of(decision):decision).subscribe(ok=>{if(ok)this.load();});}
 save(){const s=this.sport;if(!s||this.busy||!this.dirty)return;this.busy=true;this.error='';this.api.update<AcademySport>(`sports/${s.id}`,{version:s.version,training:this.training,sessions:this.sessions,active:s.active}).subscribe({next:saved=>{this.sport=saved;this.cancel();this.busy=false;this.message='Activity configuration saved.';this.feedback.success(this.message);},error:e=>{this.busy=false;this.conflict=e.status===409&&!['TRAINING_DEPENDENCY_EXISTS','SPORT_SESSION_ACTIVE','SPORT_RESERVATION_EXISTS','SINGLE_SPORT_LIMIT_EXCEEDED'].includes(e.error?.errorCode);this.error=e.error?.errorCode==='TRAINING_DEPENDENCY_EXISTS'?'Ongoing or future training prevents disabling Training & Coaching. Review Training Batches to resolve these commitments.':apiError(e).description;}});}
}
