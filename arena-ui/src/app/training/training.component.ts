import { UiFeedbackService } from '../ui/ui-feedback.service';
import { activityCategory, activityIcon } from './activity-catalogue';
import { Component, inject, OnInit, DestroyRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { forkJoin, Observable, Subscription } from 'rxjs';
import { AuthService, AcademyContext } from '../services/auth';
import { apiError } from '../api-error';
import { PageHeaderComponent, StatusComponent, EmptyStateComponent, DrawerComponent, ConfirmationComponent } from '../ui/shared';
import { TrainingService, CatalogueItem, AcademySport, AcademyStaff, TrainingBatch, Weekly, BatchDetail } from './training.service';

@Component({selector:'app-training',standalone:true,imports:[CommonModule,FormsModule,RouterLink,PageHeaderComponent,StatusComponent,EmptyStateComponent,DrawerComponent,ConfirmationComponent],templateUrl:'./training.component.html',styleUrl:'./training.component.scss'})
export class TrainingComponent implements OnInit {
 private api=inject(TrainingService);
 private feedback=inject(UiFeedbackService);
 auth=inject(AuthService);
 private route=inject(ActivatedRoute);
 private router=inject(Router);
 private destroyRef=inject(DestroyRef);
 section='sports'; menuOpen=false; staffDrawer=false; batchDrawer=false; settingsOpen=false; roleFilter=''; categoryFilter='All activities'; pageLoading=false; context:AcademyContext|null=null;
 error=''; message=''; busy=false; loading=true;
 confirmationSport:AcademySport|null=null;
 catalogue:CatalogueItem[]=[]; sports:AcademySport[]=[]; staff:AcademyStaff[]=[];
 rows:any[]=[]; page=0; totalPages=0; search=''; statusFilter=''; catalogueSearch='';
 selectedCodes=new Set<string>(); customName=''; sessions=false; training=true;
 customOptions={training:true,sessions:false};
 operatingModel(v:{training:boolean;sessions:boolean}):string {return v.training&&v.sessions?'BOTH':v.sessions?'FACILITY':'TRAINING';}
 selectionModel(code:string,mode:string):void {this.sportOptions.set(code,{training:mode!=='FACILITY',sessions:mode!=='TRAINING'});}
 updateModel(s:AcademySport,mode:string):void {this.updateSport(s,{training:mode!=='FACILITY',sessions:mode!=='TRAINING'});}
 sportOptions=new Map<string,{training:boolean;sessions:boolean}>();
 readonly categories=['All activities','Racket sports','Team sports','Fitness & wellness','Combat sports','Aquatic sports','Precision & indoor','Other activities'];
 selectedSport:AcademySport|null=null; sportTab='Overview'; sportBatchRows:TrainingBatch[]=[];
 permissionTarget:AcademyStaff|null=null;
 discountPolicy={enabled:false,maxPercent:0,maxAmount:0,dailyAllowance:null as number|null,canApprove:false};
 staffPermissions(member:AcademyStaff):void {this.permissionTarget=member;this.error='';this.discountPolicy={enabled:!!member.discountEnabled,maxPercent:member.maxDiscountPercent||0,maxAmount:member.maxDiscountAmount||0,dailyAllowance:member.dailyDiscountAllowance??null,canApprove:!!member.discountApprovalAllowed};}
 saveDiscountPolicy():void {if(!this.permissionTarget||this.busy)return;this.busy=true;this.api.update<AcademyStaff>(`staff/${this.permissionTarget.id}/discount-policy`,{version:this.permissionTarget.version,...this.discountPolicy}).subscribe({next:()=>{this.busy=false;this.permissionTarget=null;this.message='Staff discount permissions saved.';this.reload();},error:e=>{this.busy=false;this.error=apiError(e).description;}});}
 editingStaff:AcademyStaff|null=null;
 staffForm={name:'',email:'',phone:'',role:'COACH',designation:'COACH',active:true}; staffSports=new Set<number>();
 editingBatch:TrainingBatch|null=null;
 batchForm={sportId:0,name:'',code:'',level:'',capacity:null as number|null,startDate:'',endDate:'',status:'DRAFT'};
 coachIds=new Set<number>(); leadCoachId:number|null=null; weekly:Weekly[]=[];
 readonly days=['Monday','Tuesday','Wednesday','Thursday','Friday','Saturday','Sunday'];
 ngOnInit():void {
  this.route.data.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(data=>{
   this.section=data['section']||'sports';this.error='';this.message=''; this.page=0; this.search=''; this.statusFilter=''; this.editingBatch=null; this.selectedSport=null;this.staffDrawer=false;this.batchDrawer=false;this.roleFilter='';
   this.resetBatch();this.reload();
  });
 }
 reload():void {
  this.loading=true;
  forkJoin({context:this.auth.fetchAcademyContext(),catalogue:this.api.all<CatalogueItem>('catalogue'),sports:this.api.all<AcademySport>('sports'),staff:this.api.all<AcademyStaff>('staff')}).subscribe({
   next:data=>{ this.context=data.context; this.catalogue=data.catalogue; this.sports=data.sports; this.staff=data.staff; if(this.loginTarget)this.loginTarget=this.staff.find(m=>m.id===this.loginTarget?.id)||this.loginTarget; this.loading=false; this.loadPage(); },
   error:e=>{this.error=apiError(e).description;this.loading=false;}
  });
 }
 loadPage():void {
  const filters:Record<string,string|number|boolean>={page:this.page,size:20,search:this.search};
  if(this.statusFilter) filters[this.section==='batches'?'status':'active']=this.section==='batches'?this.statusFilter:this.statusFilter==='true';
  if(this.section==='batches') {const sportId=Number(this.route.snapshot.queryParamMap.get('sportId'));if(sportId)filters['sportId']=sportId;}
  this.pageLoading=true;
  if(this.section==='staff' && this.roleFilter){
   this.api.all<AcademyStaff>('staff').subscribe({next:items=>{const filtered=items.filter(m=>m.role===this.roleFilter && (!this.statusFilter || m.active===(this.statusFilter==='true')) && `${m.name} ${m.email}`.toLowerCase().includes(this.search.toLowerCase()));this.totalPages=Math.ceil(filtered.length/20);this.rows=filtered.slice(this.page*20,(this.page+1)*20);this.pageLoading=false;},error:e=>{this.error=apiError(e).description;this.pageLoading=false;}});return;
  }
  this.api.list<any>(this.section,filters).subscribe({next:p=>{this.rows=p.content;this.totalPages=p.totalPages;this.pageLoading=false;},error:e=>{this.error=apiError(e).description;this.pageLoading=false;}});
 }
 retryLoad():void {this.error='';this.reload();}
 filter():void {this.error='';this.page=0;this.loadPage();}
 paginate(delta:number):void {this.page+=delta;this.loadPage();}
 get filteredCatalogue():CatalogueItem[] {return this.catalogue.filter(c=>`${c.label} ${c.code}`.toLowerCase().includes(this.catalogueSearch.toLowerCase()) && (!!this.catalogueSearch.trim()||this.categoryFilter==='All activities'||this.category(c)===this.categoryFilter));}
 get catalogueGroups(){return this.categories.slice(1).map(name=>({name,items:this.filteredCatalogue.filter(c=>this.category(c)===name)})).filter(g=>g.items.length);}
 category=activityCategory; sportIcon=activityIcon;

 get selectedActivities(){return this.catalogue.filter(c=>this.selectedCodes.has(c.code)&&!this.isRegistered(c.code));}
 optionsFor(code:string){if(!this.sportOptions.has(code))this.sportOptions.set(code,{training:true,sessions:false});return this.sportOptions.get(code)!;}
 selectActivity(item:CatalogueItem):void {if(this.busy)return;const registered=this.sports.find(s=>s.catalogueCode===item.code);if(registered)return;else this.toggle(this.selectedCodes,item.code);}
 get title():string {return this.section==='sports'?'Sports & Activities':this.section==='staff'?'Staff & Coaches':'Training Batches';}
 get subtitle():string {return this.section==='sports'?'Build your academy’s activity offering. Select sports, configure sessions, and grow your programme.':this.section==='staff'?'Bring your team together. Manage staff records and coaching assignments.':'Organise your training programmes, assign coaches, and plan recurring schedules.';}
 get setupStep():number {return this.section==='sports'?1:this.section==='staff'?2:3;}
 newStaff():void {this.resetStaff();this.staffDrawer=true;}
 newBatch():void {this.resetBatch();this.batchDrawer=true;}
 closeStaff():void {if(!this.busy){this.cancelLoginCheck();this.staffDrawer=false;}}
 closeLogin():void {if(!this.busy){this.cancelLoginCheck();this.loginOpen=false;}}
 closeBatch():void {if(!this.busy)this.batchDrawer=false;}
 continueSetup():void {void this.router.navigate([this.section==='sports'?'/academy/staff':this.section==='staff'?'/academy/batches':'/']);}

 get facilityRouteAvailable():boolean {return this.router.config.some(r=>r.path==='academy/facilities');}
 get validSelection():boolean {return !!this.selectedActivities.length||!!this.customName.trim();}
 sportName(id:number):string {return this.sports.find(s=>s.id===id)?.name||'Activity';}
 resumeSetup():void {if(!this.context)return;this.action(this.api.update('settings',{version:this.context.version,timezone:this.context.timezone,skipped:false}));}
 get activeSports():AcademySport[] {return this.sports.filter(s=>s.active&&s.training);}
 get availableCoaches():AcademyStaff[] {return this.staff.filter(m=>m.active&&(m.role==='COACH'||m.applicationRoles?.includes('INSTRUCTOR'))&&m.sportIds.includes(Number(this.editingBatch?.sportId||this.batchForm.sportId)));}
 get sportCoaches():AcademyStaff[] {return this.staff.filter(m=>(m.role==='COACH'||m.applicationRoles?.includes('INSTRUCTOR'))&&!!this.selectedSport&&m.sportIds.includes(this.selectedSport.id));}
 get facilityLabel():string {return 'Play Areas';}
 toggle<T>(set:Set<T>,value:T):void {set.has(value)?set.delete(value):set.add(value);}
 isRegistered(code:string):boolean {return this.sports.some(s=>s.catalogueCode===code);}
 private action<T>(request:Observable<T>,done?:(value:T)=>void,reload=true):void {
  if(this.busy)return; this.busy=true;this.error='';this.message='';
  request.subscribe({next:value=>{this.busy=false;this.message='Saved successfully.';done?.(value);if(reload)this.reload();},error:e=>{
   this.busy=false;this.error=apiError(e).description;if(e.status===403)this.auth.fetchBusinessBranding().subscribe({error:()=>this.auth.setBusinessBranding(null)});if(this.section==='staff')this.reload();
  }});
 }
 registerSports():void {
  if(this.busy)return;
  const activities=[...[...this.selectedCodes].filter(code=>!this.isRegistered(code)).map(catalogueCode=>({catalogueCode,customName:null as string|null}))];
  if(this.customName.trim())activities.push({catalogueCode:'OTHER',customName:this.customName.trim()});
  if(!activities.length)return;this.busy=true;this.error='';
  this.api.create<AcademySport[]>('sports/bulk',{activities}).subscribe({next:saved=>{this.busy=false;this.selectedCodes.clear();this.customName='';this.message=`${saved.length} activities registered.`;this.reload();},error:e=>{this.busy=false;this.error=apiError(e).description;}});
 }
 updateSport(s:AcademySport,changes:Partial<AcademySport>):void {this.action(this.api.update<AcademySport>(`sports/${s.id}`,{version:s.version,training:s.training,sessions:s.sessions,active:s.active,...changes}),saved=>{if(this.selectedSport?.id===saved.id)this.selectedSport=saved;});}
 requestSportStatus(s:AcademySport):void {if(s.active)this.confirmationSport=s;else this.updateSport(s,{active:true});}
 confirmSportStatus():void {const sport=this.confirmationSport;this.confirmationSport=null;if(sport)this.updateSport(sport,{active:false});}
 chooseSport(s:AcademySport):void {this.selectedSport=s;this.sportTab='Overview';}
 selectSportTab(tab:string):void {this.sportTab=tab;if(tab==='Batches'&&this.selectedSport)this.api.list<TrainingBatch>('batches',{sportId:this.selectedSport.id,size:20}).subscribe({next:p=>this.sportBatchRows=p.content,error:e=>this.error=apiError(e).description});}
 assignSportCoach(m:AcademyStaff,s:AcademySport):void {const ids=new Set(m.sportIds);this.toggle(ids,s.id);this.action(this.api.update<AcademyStaff>(`staff/${m.id}/sports`,{version:m.version,sportIds:[...ids]}));}
 openFacilities(s:AcademySport):void {if(s.catalogueCode)this.auth.setActiveSport(s.catalogueCode);}
 openSportBatches():void {void this.router.navigate(['/academy/batches'],{queryParams:{sportId:this.selectedSport?.id}});}
 skip():void {if(!this.context)return;this.action(this.api.update('settings',{version:this.context.version,timezone:this.context.timezone,skipped:true}),()=>void this.router.navigate(['/']),false);}
 saveTimezone():void {if(!this.context)return;this.action(this.api.update('settings',{version:this.context.version,timezone:this.context.timezone,skipped:this.context.skipped}));}
 resetStaff():void {this.error='';this.message='';this.cancelLoginCheck();this.staffFieldErrors={};this.registrationRequestId='';this.loginAtRegistration=false;this.loginForm={username:'',email:''};this.loginRoles=new Set(['INSTRUCTOR']);this.editingStaff=null;this.staffForm={name:'',email:'',phone:'',role:'COACH',designation:'COACH',active:true};this.staffSports.clear();}
 editStaff(m:AcademyStaff):void {this.staffDrawer=true;this.editingStaff=m;this.staffForm={name:m.name,email:m.email||'',phone:m.phone||'',role:m.role,designation:m.designation|| (m.role==='COACH'?'COACH':'GENERAL_STAFF'),active:m.active};this.staffSports=new Set(m.sportIds);}
 saveStaff():void {
  if(this.busy||this.checkingLogin)return;
  const m=this.editingStaff;
  if(!this.staffForm.name.trim()||(this.staffForm.email&&!this.validLoginEmail(this.staffForm.email)))return;
  this.staffForm.role=this.staffForm.designation==='COACH'?'COACH':'STAFF';
  if(!m&&this.loginAtRegistration){if(!this.validLoginCredentials||!this.loginRoles.size)return;this.checkLoginAvailability(()=>this.registerStaff());return;}
  const request=m?this.api.update<AcademyStaff>(`staff/${m.id}`,{...this.staffForm,version:m.version}):this.api.create<AcademyStaff>('staff',this.staffForm);
  this.action(request,saved=>{this.editStaff(saved);this.feedback.success('Staff membership saved.');});
 }
 private registerStaff():void {
  if(this.busy)return;this.busy=true;this.error='';this.message='';this.staffFieldErrors={};
  this.api.create<AcademyStaff>('staff/registration',{...this.staffForm,email:this.staffForm.email.trim(),username:this.loginForm.username.trim(),roles:[...this.loginRoles],requestId:this.ensureRegistrationId()}).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({next:saved=>{this.busy=false;this.loginAtRegistration=false;this.registrationRequestId='';this.editStaff(saved);this.registrationFeedback(saved);this.reload();},error:e=>{this.busy=false;this.loginValidationError(e);}});
 }
 private registrationFeedback(saved:AcademyStaff):void {this.feedback.success('Staff membership and login access created.');if(saved.notificationState!=='SENT')this.feedback.notify('warning','Staff was created. Account setup email needs attention; use Resend setup email.');}
 retryRegistration(m:AcademyStaff):void {if(this.busy)return;this.busy=true;this.error='';this.api.create<AcademyStaff>(`staff/${m.id}/registration/retry`,{}).subscribe({next:saved=>{this.busy=false;this.registrationFeedback(saved);this.reload();},error:e=>{this.busy=false;this.error=apiError(e).description;this.reload();}});}
 checkingLogin=false;staffFieldErrors:Record<string,string>={};private checkedCredentials='';private loginCheck?:Subscription;private registrationRequestId='';
 private ensureRegistrationId():string {if(!this.registrationRequestId){const b=crypto.getRandomValues(new Uint8Array(16));b[6]=(b[6]&15)|64;b[8]=(b[8]&63)|128;const h=Array.from(b,x=>x.toString(16).padStart(2,'0')).join('');this.registrationRequestId=`${h.slice(0,8)}-${h.slice(8,12)}-${h.slice(12,16)}-${h.slice(16,20)}-${h.slice(20)}`;}return this.registrationRequestId;}
 private validLoginEmail(email:string):boolean{return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())&&email.trim().length<=255;}
 get validLoginCredentials():boolean {return /^[A-Za-z0-9._@+-]{1,100}$/.test(this.loginForm.username)&&this.validLoginEmail(this.loginEmail);}
 private get loginEmail():string{return this.loginOpen?this.loginForm.email:this.staffForm.email;}
 private cancelLoginCheck():void {this.loginCheck?.unsubscribe();this.checkingLogin=false;this.checkedCredentials='';}
 credentialsChanged(field:string):void {this.cancelLoginCheck();delete this.staffFieldErrors[field];this.error='';}
 checkLoginAvailability(done?:()=>void):void {
  if((!this.loginAtRegistration&&!this.loginOpen)||this.editingStaff&&!this.loginOpen||!this.validLoginCredentials||this.busy)return;
  const username=this.loginForm.username.toLowerCase(),email=this.loginEmail.trim().toLowerCase(),key=username+'|'+email+'|'+(this.loginOpen?this.loginTarget?.id:'');
  if(this.checkedCredentials===key){done?.();return;}if(this.checkingLogin)return;
  this.checkingLogin=true;this.staffFieldErrors={};
  this.loginCheck=this.api.create<{available:boolean}>('staff/login-availability',{username,email,...(this.loginOpen?{staffId:this.loginTarget?.id}:{requestId:this.ensureRegistrationId()})}).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({next:()=>{this.checkingLogin=false;this.checkedCredentials=key;done?.();},error:e=>{this.checkingLogin=false;this.loginValidationError(e);}});
 }
 private loginValidationError(e:any):void {const code=e.error?.errorCode;this.error=apiError(e).description;if(code==='DUPLICATE_USERNAME')this.staffFieldErrors['username']=this.error;if(code==='DUPLICATE_EMAIL')this.staffFieldErrors['email']=this.error;}
 assignStaffSports():void {if(!this.editingStaff)return;this.action(this.api.update<AcademyStaff>(`staff/${this.editingStaff.id}/sports`,{version:this.editingStaff.version,sportIds:[...this.staffSports]}),m=>this.editStaff(m));}
 loginStatus(m:AcademyStaff):string {return ({PROVISIONING:'Login setup in progress',NOT_ENABLED:'Not Enabled',INVITATION_PENDING:'Invitation Pending',PENDING:'Invitation Pending',ACTIVE:'Active',DISABLED:'Disabled',INVITATION_EXPIRED:'Invitation Expired',PROVISIONING_FAILED:'Provisioning Failed',LEGACY_LINKED:'Legacy account · review required'} as Record<string,string>)[m.loginAccessStatus||m.loginState||''] || (m.appUserId==null?'Not Enabled':'Legacy account · review required');}
 get canManagePersonnel():boolean {return !!this.auth.businessBrandingValue?.permissions.manageOrdinaryStaff;}
 canEditMember(m:AcademyStaff):boolean {return this.canManagePersonnel&&(this.auth.hasRole('OWNER')||!(m.designation==='MANAGER'||m.applicationRoles?.includes('MANAGER')));}
 get permittedLoginRoles():string[]{return this.auth.hasRole('OWNER')?['MANAGER','INSTRUCTOR','STAFF']:['INSTRUCTOR','STAFF'];}
 managerAccess='STANDARD';
 saveManagerAccess():void {const m=this.loginTarget;if(!m||!this.auth.hasRole('OWNER'))return;this.action(this.api.update<AcademyStaff>(`staff/${m.id}/login/manager-access`,{version:m.version,accessLevel:this.managerAccess}),saved=>{this.loginTarget=saved;this.managerAccess=saved.managerAccess||'STANDARD';});}
 loginAtRegistration=false;
 loginForm={username:'',email:''};loginRoles=new Set<string>(['INSTRUCTOR']);loginOpen=false;loginTarget:AcademyStaff|null=null;disableTarget:AcademyStaff|null=null;
 defaultLoginRole():void {if(!this.editingStaff)this.loginRoles=new Set([this.staffForm.designation==='MANAGER'?'MANAGER':this.staffForm.designation==='COACH'?'INSTRUCTOR':'STAFF']);}
 openLogin(m:AcademyStaff):void {this.cancelLoginCheck();this.staffFieldErrors={};this.loginTarget=m;this.managerAccess=m.managerAccess||'STANDARD';this.loginForm={username:m.loginUsername||'',email:m.email||''};this.loginRoles=new Set(m.applicationRoles?.length?m.applicationRoles:[m.designation==='MANAGER'?'MANAGER':m.role==='COACH'?'INSTRUCTOR':'STAFF']);this.error='';this.loginOpen=true;}
 enableLogin():void {const m=this.loginTarget;if(!m||!this.loginRoles.size||this.busy||this.checkingLogin||!this.validLoginCredentials)return;this.checkLoginAvailability(()=>{this.busy=true;this.error='';this.api.create<AcademyStaff>(`staff/${m.id}/login`,{version:m.version,...this.loginForm,roles:[...this.loginRoles]}).subscribe({next:saved=>{this.busy=false;this.loginTarget=saved;this.loginOpen=false;this.feedback.success('Login access enabled.');this.reload();},error:e=>{this.busy=false;this.loginValidationError(e);this.reload();}});});}
 loginAction(m:AcademyStaff,action:string):void {this.action(this.api.create<AcademyStaff>(`staff/${m.id}/login/${action}`,{version:m.version}),()=>{this.disableTarget=null;});}
 saveRoles():void {const m=this.loginTarget;if(!m||!this.loginRoles.size)return;this.action(this.api.update<AcademyStaff>(`staff/${m.id}/login/roles`,{version:m.version,roles:[...this.loginRoles]}),saved=>{this.loginTarget=saved;this.loginOpen=false;});}

 resetBatch():void {
  this.editingBatch=null;this.batchForm={sportId:Number(this.route.snapshot.queryParamMap.get('sportId'))||0,name:'',code:'',level:'',capacity:null,startDate:'',endDate:'',status:'DRAFT'};
  this.coachIds.clear();this.leadCoachId=null;this.weekly=[];
 }
 editBatch(b:TrainingBatch):void {
  this.api.detail(b.id).subscribe({next:d=>this.applyDetail(d),error:e=>this.error=apiError(e).description});
 }
 private applyDetail(d:BatchDetail):void {
  const b=d.batch;this.batchDrawer=true;this.editingBatch=b;
  this.batchForm={sportId:b.sportId,name:b.name,code:b.code,level:b.level||'',capacity:b.capacity||null,startDate:b.startDate,endDate:b.endDate||'',status:b.status};
  this.coachIds=new Set(b.coachIds);this.leadCoachId=b.leadCoachId||null;this.weekly=d.schedules.map(s=>({...s,effectiveTo:s.effectiveTo||''}));
 }
 saveBatch():void {
  if(this.busy)return;
  const b=this.editingBatch;const body={...this.batchForm,endDate:this.batchForm.endDate||null,version:b?.version};
  this.action(b?this.api.update<TrainingBatch>(`batches/${b.id}`,body):this.api.create<TrainingBatch>('batches',body),saved=>this.editBatch(saved));
 }
 saveCoaches():void {if(!this.editingBatch)return;this.action(this.api.update<TrainingBatch>(`batches/${this.editingBatch.id}/coaches`,{version:this.editingBatch.version,coachIds:[...this.coachIds],leadCoachId:this.leadCoachId}),b=>this.editingBatch=b);}
 addSchedule():void {this.weekly.push({dayOfWeek:1,startTime:'',endTime:'',effectiveFrom:this.editingBatch?.startDate||this.batchForm.startDate,effectiveTo:this.editingBatch?.endDate||''});}
 saveSchedules():void {if(!this.editingBatch)return;this.action(this.api.update<BatchDetail>(`batches/${this.editingBatch.id}/schedules`,{version:this.editingBatch.version,schedules:this.weekly.map(s=>({...s,effectiveTo:s.effectiveTo||null}))}),d=>this.applyDetail(d));}
}
