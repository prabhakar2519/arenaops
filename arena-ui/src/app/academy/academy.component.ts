import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../services/auth';
import { apiError } from '../api-error';
import { PageHeaderComponent, StatusComponent, DrawerComponent, EmptyStateComponent } from '../ui/shared';
import { AcademySport } from '../training/training.service';

interface Step {key:string;label:string;applicable:boolean;complete:boolean;path:string;}
interface Progress {percentage:number;status:string;steps:Step[];ownerManaged:boolean;reviewConfirmed:boolean;version:number;profile:{name:string;ownerName:string;email:string;phone:string;timezone:string};}
interface Facility {id:number;version:number;sportId:number;name:string;code:string;kind:string;capacity:number|null;active:boolean;}
@Component({selector:'app-academy',standalone:true,imports:[CommonModule,FormsModule,RouterLink,PageHeaderComponent,StatusComponent,DrawerComponent,EmptyStateComponent],template:`
 <div class="ao-workspace">
 <ao-page-header [title]="page==='facilities'?'Play Areas':page==='setup'?'Academy Setup':'Dashboard'" [subtitle]="page==='dashboard'?'':progress?.profile?.name || 'Your academy workspace'"><button *ngIf="page==='facilities'&&canManage" class="btn-primary" (click)="newFacility()">＋ Add Play Area</button></ao-page-header>
 <p *ngIf="loading" role="status">{{page==='dashboard'?'Loading setup completion…':'Loading academy…'}}</p><p *ngIf="error" class="notice error" role="alert">{{error}}</p><p *ngIf="message" class="notice success" role="status">{{message}}</p>
 <section class="panel setup-widget" *ngIf="progress && page==='setup'" [class.ready]="progress.status==='READY'">
  <div class="panel-heading"><div><h2>Academy setup · {{progress.percentage}}%</h2><p class="hint">{{progress.status==='READY'?'Your academy setup is ready.':'Complete your setup at your own pace. You can keep using your dashboard.'}}</p></div><ao-status [status]="progress.status"></ao-status></div>
  <div class="panel-body"><progress [value]="progress.percentage" max="100" [attr.aria-label]="'Academy setup '+progress.percentage+' percent'"></progress>
  <ul class="setup-list" *ngIf="page==='setup'||progress.status!=='READY'"><li *ngFor="let step of progress.steps" [class.not-applicable]="!step.applicable"><span aria-hidden="true">{{!step.applicable?'—':step.complete?'✓':'○'}}</span><a [routerLink]="step.path">{{step.label}}</a><span>{{!step.applicable?'Not applicable':step.complete?'Complete':'Pending'}}</span></li></ul>
  <a *ngIf="page!=='setup'&&progress.status!=='READY'" class="btn btn-primary" [routerLink]="continuePath">Continue Setup</a><a *ngIf="page!=='setup'&&progress.status==='READY'" class="btn" routerLink="/academy/setup">Review setup</a></div>
 </section>
 <p *ngIf="page==='setup'&&progress&&!auth.hasRole('OWNER')" class="hint">Your academy owner manages the profile, staffing confirmation, and final setup review.</p>
 <ng-container *ngIf="page==='setup'&&progress&&auth.hasRole('OWNER')">
  <section class="panel"><h2>Academy profile</h2><form #profileForm="ngForm" (ngSubmit)="saveProfile()" class="form-grid">
   <label>Academy name<input name="academyName" [(ngModel)]="profile.name" required maxlength="255"></label><label>Owner name<input name="ownerName" [(ngModel)]="profile.ownerName" required maxlength="255"></label><label>Contact email<input type="email" name="email" [(ngModel)]="profile.email" required email></label><label>Contact mobile<input name="phone" [(ngModel)]="profile.phone" required maxlength="50"></label><label>Timezone<input name="timezone" [(ngModel)]="profile.timezone" required></label><button class="btn-primary" [disabled]="profileForm.invalid||busy">Save profile</button>
  </form></section>
  <section class="panel"><h2>Staffing and setup review</h2><label class="check-label"><input type="checkbox" [(ngModel)]="ownerManaged">I manage the academy and coaching without additional staff</label><button (click)="confirm(false)" [disabled]="busy">Save staffing choice</button><p class="hint">Review every applicable step before confirming. Setup progress does not restrict your licence or login.</p><button class="btn-primary" (click)="reviewPrompt=true" [disabled]="busy||progress.status!=='REVIEW_NEEDED'">Confirm setup review</button></section>
 </ng-container>
 <ao-drawer *ngIf="reviewPrompt" title="Confirm academy setup" (close)="reviewPrompt=false"><p>I have reviewed the academy profile, activities, staffing, and all applicable batch and play area settings.</p><button class="btn-primary" (click)="confirm(true)" [disabled]="busy">Confirm setup</button><button (click)="reviewPrompt=false">Cancel</button></ao-drawer>
 <section class="panel dashboard-completion" *ngIf="page==='dashboard' && !loading && !error && progress && canManage" [class.completed]="setupCompleted" aria-labelledby="setup-completion-title">
  <div class="completion-heading"><span *ngIf="setupCompleted" class="completion-icon" aria-hidden="true">✓</span><h2 id="setup-completion-title">{{setupCompleted?'Academy setup completed':'Complete your academy setup'}}</h2><span class="completion-badge">{{progress.percentage}}%</span></div>
  <p class="hint">{{setupCompleted?'Your workspace is ready to use.':outstandingDescription}}</p>
  <progress *ngIf="!setupCompleted" [value]="progress.percentage" max="100" [attr.aria-label]="'Academy setup '+progress.percentage+' percent'"></progress>
  <a *ngIf="setupCompleted" class="review-setup" routerLink="/academy/setup">Review setup</a>
  <a *ngIf="!setupCompleted && dashboardContinuePath" class="btn btn-primary" [routerLink]="dashboardContinuePath">{{dashboardContinuePath==='/academy/sports'?'Manage Activities':'Continue setup'}}</a>
  <a *ngIf="!setupCompleted && dashboardContinuePath!=='/academy/sports' && activitiesIncomplete" class="review-setup" routerLink="/academy/sports">Manage Activities</a>
  <p *ngIf="!setupCompleted && !dashboardContinuePath" class="hint owner-setup-note">Your academy owner can complete the remaining setup steps.</p>
 </section>
 <p *ngIf="page==='dashboard' && !loading && !canManage" class="hint">Setup completion information is available to academy owners and managers.</p>
 <section class="panel" *ngIf="page==='facilities'"><h2>Academy play areas</h2><p class="hint">Configure courts, tables, fields, or other facilities for booking-enabled activities.</p><div class="table-scroll" *ngIf="facilities.length"><table class="data-table"><thead><tr><th>Name</th><th>Sport</th><th>Type</th><th>Status</th><th>Actions</th></tr></thead><tbody><tr *ngFor="let f of facilities"><td>{{f.name}}<div class="cell-meta">{{f.code}}</div></td><td>{{sportName(f.sportId)}}</td><td>{{f.kind}}</td><td><ao-status [status]="f.active?'Active':'Inactive'"></ao-status></td><td><button (click)="editFacility(f)">Edit</button></td></tr></tbody></table></div><ao-empty *ngIf="!facilities.length&&!loading" title="Configure your first play area" description="Enable Facility or Both on a registered sport, then add its courts or tables."><a routerLink="/academy/sports">Configure activities</a></ao-empty></section>
 <ao-drawer *ngIf="facilityOpen" [title]="editing?'Edit play area':'Add play area'" (close)="facilityOpen=false"><p *ngIf="error" class="notice error" role="alert">{{error}}</p><form #areaForm="ngForm" (ngSubmit)="saveFacility()" class="form-grid"><label>Activity<select name="sportId" [(ngModel)]="area.sportId" required><option [ngValue]="0">Select activity</option><option *ngFor="let s of facilitySports" [ngValue]="s.id">{{s.name}}</option></select></label><label>Name<input name="name" [(ngModel)]="area.name" required maxlength="160"></label><label>Code<input name="code" [(ngModel)]="area.code" required pattern="[A-Za-z0-9_-]+" maxlength="64"></label><label>Type<select name="kind" [(ngModel)]="area.kind"><option>COURT</option><option>TABLE</option><option>FIELD</option><option>OTHER</option></select></label><label>Capacity (optional)<input type="number" name="capacity" [(ngModel)]="area.capacity" min="1"></label><label><input type="checkbox" name="active" [(ngModel)]="area.active">Active</label><button class="btn-primary" [disabled]="areaForm.invalid||!area.sportId||busy">Save play area</button></form></ao-drawer>
 </div>
 `,styles:[`.panel{margin-bottom:var(--space-5)}h2{font-family:var(--font-heading);font-size:var(--text-section)}progress{width:100%;height:12px;accent-color:var(--brand);margin:var(--space-4) 0}.setup-list{list-style:none;padding:0}.setup-list li{display:flex;gap:var(--space-3);padding:var(--space-3) 0;border-bottom:1px solid var(--border-main)}.setup-list li span:last-child{margin-left:auto;color:var(--text-muted);font-size:var(--text-sm)}.not-applicable{opacity:.7}.form-grid{max-width:760px;display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:var(--space-4)}.form-grid label{display:flex;flex-direction:column;gap:8px}.check-label{display:flex;gap:12px;margin:16px 0}input[type=checkbox]{width:auto;min-height:20px}:host{display:block}.panel:not(.setup-widget){padding:var(--space-5)}.panel:not(.setup-widget)>h2{margin-bottom:var(--space-4)}.setup-list{margin:0 0 var(--space-4)}.setup-list a{color:var(--text-main);text-decoration:none;flex:1}.setup-list a:hover{color:var(--brand);text-decoration:underline}.dashboard-completion{max-width:880px}.completion-heading{display:flex;align-items:center;gap:var(--space-3)}.completion-heading h2{margin:0!important;flex:1;min-width:0}.completion-icon{display:grid;place-items:center;width:30px;height:30px;flex-shrink:0;border-radius:50%;background:var(--brand-soft);color:var(--brand);font-weight:var(--weight-bold)}.completion-badge{font-size:var(--text-sm);font-weight:var(--weight-semibold);color:var(--text-muted);white-space:nowrap}.completed .completion-badge{color:var(--brand);background:var(--brand-soft);padding:4px 8px;border-radius:var(--radius-sm)}.dashboard-completion>.hint{margin:var(--space-3) 0 var(--space-4)}.dashboard-completion progress{display:block;margin:0 0 var(--space-4)}.review-setup{font-size:var(--text-sm);color:var(--brand);text-decoration:none}.review-setup:hover{text-decoration:underline}.owner-setup-note{margin-bottom:0!important}@media(max-width:600px){.form-grid{grid-template-columns:1fr}.setup-list li{flex-wrap:wrap}}`]})
export class AcademyComponent implements OnInit {
 auth=inject(AuthService);private http=inject(HttpClient);private route=inject(ActivatedRoute);
 page='dashboard';loading=true;busy=false;error='';message='';progress:Progress|null=null;ownerManaged=false;reviewPrompt=false;
 profile={name:'',ownerName:'',email:'',phone:'',timezone:'Asia/Kolkata'};facilities:Facility[]=[];sports:AcademySport[]=[];
 facilityOpen=false;editing:Facility|null=null;area={sportId:0,name:'',code:'',kind:'COURT',capacity:null as number|null,active:true};
 get canManage(){return this.auth.hasRole('OWNER')||this.auth.hasRole('MANAGER');}
 get continuePath(){return this.progress?.steps.find(s=>s.applicable&&!s.complete)?.path||'/academy/setup';}
 get setupCompleted(){return this.progress?.percentage===100 && this.progress?.status==='READY';}
 get outstandingSteps(){return this.progress?.steps?.filter(step=>step.applicable&&!step.complete)||[];}
 get outstandingDescription(){return this.outstandingSteps.length?'Remaining: '+this.outstandingSteps.map(step=>step.label).join(', ')+'.':'Review the required setup steps to finish your workspace.';}
 get activitiesIncomplete(){return this.outstandingSteps.some(step=>step.key==='SPORTS');}
 get dashboardContinuePath(){
  if(!this.canManage)return null;
  const step=this.outstandingSteps.find(step=>this.auth.hasRole('OWNER')||['SPORTS','STAFF','BATCHES','FACILITIES'].includes(step.key));
  return step?.path || (this.auth.hasRole('OWNER')?'/academy/setup':null);
 }
 private loadDashboard(){
  this.progress=null;this.error='';this.message='';this.loading=true;
  if(!this.canManage){this.loading=false;return;}
  this.http.get<Progress>('/api/academy/setup').subscribe({next:p=>{
   if(!p||typeof p.percentage!=='number'||!Number.isFinite(p.percentage)||p.percentage<0||p.percentage>100||!['READY','INCOMPLETE','REVIEW_NEEDED'].includes(p.status)||(p.percentage===100)!==(p.status==='READY')){
    this.error='Setup completion information is currently unavailable.';
   }else{this.progress=p;}
   this.loading=false;
  },error:e=>{this.error=apiError(e).description;this.loading=false;}});
 }
 get facilitySports(){return this.sports.filter(s=>s.active&&s.sessions);}
 sportName(id:number){return this.sports.find(s=>s.id===id)?.name||'Activity';}
 ngOnInit(){this.route.data.subscribe(data=>{this.page=data['page']||'dashboard';this.load();});}
 load(){if(this.page==='dashboard'){this.loadDashboard();return;}this.loading=true;this.error='';if(this.canManage){this.http.get<Progress>('/api/academy/setup').subscribe({next:p=>{this.progress=p;this.profile={...p.profile};this.ownerManaged=p.ownerManaged;this.loading=false;},error:e=>{this.error=apiError(e).description;this.loading=false;}});}else{this.loading=false;}
 if(this.page==='facilities'){this.http.get<Facility[]>('/api/academy/facilities').subscribe({next:r=>this.facilities=r,error:e=>this.error=apiError(e).description});this.http.get<{content:AcademySport[]}>('/api/academy/sports?size=100').subscribe({next:r=>this.sports=r.content,error:e=>this.error=apiError(e).description});}}
 saveProfile(){if(!this.progress||this.busy)return;this.save('/api/academy/setup/profile',{version:this.progress.version,...this.profile});}
 confirm(review:boolean){if(!this.progress||this.busy)return;this.save('/api/academy/setup/confirmation',{version:this.progress.version,ownerManaged:this.ownerManaged,reviewConfirmed:review});}
 private save(url:string,body:unknown){this.busy=true;this.error='';this.http.put<Progress>(url,body).subscribe({next:p=>{this.progress=p;this.busy=false;this.reviewPrompt=false;this.message='Saved successfully.';},error:e=>{this.busy=false;this.error=apiError(e).description;}});}
 newFacility(){this.editing=null;this.area={sportId:0,name:'',code:'',kind:'COURT',capacity:null,active:true};this.error='';this.facilityOpen=true;}
 editFacility(f:Facility){this.editing=f;this.area={sportId:f.sportId,name:f.name,code:f.code,kind:f.kind,capacity:f.capacity,active:f.active};this.error='';this.facilityOpen=true;}
 saveFacility(){if(this.busy||!this.area.sportId)return;this.busy=true;this.error='';const request=this.editing?this.http.put(`/api/academy/facilities/${this.editing.id}`,{...this.area,version:this.editing.version}):this.http.post('/api/academy/facilities',this.area);request.subscribe({next:()=>{this.busy=false;this.facilityOpen=false;this.message='Play area saved.';this.load();},error:e=>{this.busy=false;this.error=apiError(e).description;}});}
}
