import {feedbackURL,UiFeedbackService,feedbackStub} from './feedback-fixture.mjs';
import '@angular/compiler';
import assert from 'node:assert/strict';
import { test } from 'node:test';
import fs from 'node:fs';
import { createRequire } from 'node:module';
import { pathToFileURL } from 'node:url';
import ts from 'typescript';
import { HttpClient } from '@angular/common/http';
import { createEnvironmentInjector, runInInjectionContext } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { of, throwError, Subject } from 'rxjs';
const require=createRequire(import.meta.url);
async function load(path,aliases={}) {
 let code=ts.transpileModule(fs.readFileSync(new URL(path,import.meta.url),'utf8'),{compilerOptions:{module:ts.ModuleKind.ES2022,target:ts.ScriptTarget.ES2022,experimentalDecorators:true}}).outputText;
 code=code.replace(/from ['"]([^'"]+)['"]/g,(_m,module)=>`from '${aliases[module]||pathToFileURL(require.resolve(module)).href}'`);
 const url='data:text/javascript;base64,'+Buffer.from(code).toString('base64');return {url,module:await import(url)};
}
const authURL='data:text/javascript;base64,'+Buffer.from('export class AuthService {}').toString('base64');
const {AuthService}=await import(authURL);
const errors=await load('../src/app/api-error.ts');
const apiModule=await load('../src/app/training/training.service.ts');const {TrainingService}=apiModule.module;
const panel=await load('../src/app/ui/dialog-panel.component.ts');
const shared=await load('../src/app/ui/shared.ts',{'./dialog-panel.component':panel.url});
const catalogue=await load('../src/app/training/activity-catalogue.ts');
const componentModule=await load('../src/app/training/training.component.ts',{'./activity-catalogue':catalogue.url,'../ui/ui-feedback.service':feedbackURL,'../services/auth':authURL,'../api-error':errors.url,'./training.service':apiModule.url,'../ui/shared':shared.url});
const {TrainingComponent}=componentModule.module;
const callback=await load('../src/app/auth-callback/auth-callback.component.ts',{'../ui/ui-feedback.service':feedbackURL,'../services/auth':authURL,'../api-error':errors.url});
const cricket={id:1,version:0,name:'Cricket',catalogueCode:'CRICKET',active:true,training:true,sessions:false};
const badminton={...cricket,id:2,name:'Badminton',catalogueCode:'BADMINTON',sessions:true};
const coach={id:3,version:1,name:'Coach',role:'COACH',active:true,sportIds:[1],email:'coach@example.test'};
function component(api={},section='sports',query=null) {
 const navigation=[];const context={eligible:true,timezone:'Asia/Kolkata',version:0,setupComplete:false};
 const injector=createEnvironmentInjector([{provide:UiFeedbackService,useValue:feedbackStub},
  {provide:TrainingService,useValue:{all:path=>of(path==='sports'?[cricket,badminton]:path==='staff'?[coach]:[]),list:()=>of({content:[],totalPages:0}),...api}},
  {provide:AuthService,useValue:{fetchAcademyContext:()=>of(context),setActiveSport:()=>{}}},
  {provide:ActivatedRoute,useValue:{data:of({section}),snapshot:{queryParamMap:{get:()=>query}}}},
  {provide:Router,useValue:{navigate:(...args)=>{navigation.push(args);return Promise.resolve(true);}}}
 ]);
 const value=runInInjectionContext(injector,()=>new TrainingComponent());value.ngOnInit();return {value,injector,navigation};
}
test('training APIs use current-owner URLs and versioned requests without academy IDs',()=>{
 const calls=[];const injector=createEnvironmentInjector([{provide:UiFeedbackService,useValue:feedbackStub},{provide:HttpClient,useValue:{get:(url,options)=>{calls.push({url,options});return of({});},put:(url,body)=>{calls.push({url,body});return of({});}}}]);
 const service=runInInjectionContext(injector,()=>new TrainingService());service.list('batches',{page:2,sportId:1,status:'ACTIVE'});service.update('batches/4/coaches',{version:3,coachIds:[5],leadCoachId:5});
 assert.equal(calls[0].url,'/api/academy/batches');assert.equal(calls[0].options.params.get('page'),'2');assert.deepEqual(calls[1].body,{version:3,coachIds:[5],leadCoachId:5});injector.destroy();
});
test('assignment choices include only active coaches assigned to the batch sport',()=>{
 const {value,injector}=component();value.batchForm.sportId=1;value.staff=[coach,{...coach,id:4,active:false},{...coach,id:5,role:'STAFF'},{...coach,id:6,sportIds:[2]}];
 assert.deepEqual(value.availableCoaches.map(c=>c.id),[3]);injector.destroy();
});
test('failed bulk registration preserves the complete selection',()=>{
 let calls=0;const {value,injector}=component({create:(path,body)=>{calls++;assert.equal(path,'sports/bulk');assert.equal(body.activities.length,2);return throwError(()=>({status:503}));}});
 value.sports=[];value.selectedCodes=new Set(['CRICKET','YOGA']);value.registerSports();assert.equal(calls,1);assert.deepEqual([...value.selectedCodes],['CRICKET','YOGA']);assert.equal(value.busy,false);injector.destroy();
});
test('staff registration without email sends only personnel fields and never invites',()=>{
 let body,invites=0;const {value,injector}=component({create:(path,data)=>{assert.equal(path,'staff');body=data;return of({...coach,email:null,appUserId:null});},invite:()=>{invites++;return of({});}},'staff');
 value.staffForm.name='Coach';value.staffForm.phone='9876543210';value.saveStaff();
 assert.equal(body.email,'');assert.equal(body.name,'Coach');assert.ok(!('username' in body));assert.equal(invites,0);assert.equal(value.editingStaff.appUserId,null);assert.equal(value.loginStatus(value.editingStaff),'Not Enabled');injector.destroy();
});
test('staff email is optional and login labels never infer activation from a legacy link',()=>{
 let invites=0;const {value,injector}=component({create:()=>of({...coach,appUserId:null}),invite:()=>{invites++;return of({});}},'staff');
 value.staffForm.email='coach@example.test';value.saveStaff();assert.equal(invites,0);assert.match(value.loginStatus({...coach,appUserId:99}),/review required/);injector.destroy();
});
test('a pending mutation blocks duplicate requests',()=>{
 let count=0;const pending=new Subject();const {value,injector}=component({create:()=>{count++;return pending;}},'staff');value.staffForm.name='Coach';value.saveStaff();value.saveStaff();assert.equal(count,1);pending.next(coach);pending.complete();assert.equal(value.busy,false);injector.destroy();
});
test('schedule saves normalize optional dates and pass the latest batch version',()=>{
 let body;const batch={id:8,version:4,sportId:1,name:'A',code:'A',startDate:'2026-01-01',status:'DRAFT',coachIds:[3]};
 const {value,injector}=component({update:(_p,b)=>{body=b;return of({batch:{...batch,version:5},schedules:[]});}},'batches');value.editingBatch=batch;value.weekly=[{dayOfWeek:1,startTime:'09:00',endTime:'10:00',effectiveFrom:'2026-01-01',effectiveTo:''}];value.saveSchedules();assert.equal(body.version,4);assert.equal(body.schedules[0].effectiveTo,null);assert.equal(value.editingBatch.version,5);injector.destroy();
});
test('sport-specific batches are filtered using the route and initial form selection',()=>{
 let filters;const {value,injector}=component({list:(_p,f)=>{filters=f;return of({content:[],totalPages:0});}},'batches','2');assert.equal(filters.sportId,2);assert.equal(value.batchForm.sportId,2);injector.destroy();
});
test('skipping setup persists the choice and returns to the normal workspace',()=>{
 let body;const {value,navigation,injector}=component({update:(_p,b)=>{body=b;return of({});}});value.skip();assert.equal(body.skipped,true);assert.equal(body.timezone,'Asia/Kolkata');assert.deepEqual(navigation[0][0],['/']);injector.destroy();
});
test('owner login reaches dashboard for academy and club contexts',()=>{
 for(const [context,target] of [[{eligible:false,workspaceEligible:true},'/academy/dashboard'],[{eligible:true,setupComplete:false,skipped:false},'/academy/dashboard'],[{eligible:false},'/academy/dashboard'],[{eligible:true,setupComplete:false,skipped:true},'/academy/dashboard'],[{eligible:true,setupComplete:true},'/academy/dashboard']]) {
  const calls=[];const injector=createEnvironmentInjector([{provide:UiFeedbackService,useValue:feedbackStub},{provide:AuthService,useValue:{setParlours:()=>{},setParlourName:()=>{},setActiveSport:()=>{},fetchAcademyContext:()=>of(context)}},{provide:Router,useValue:{navigate:args=>{calls.push(args);return Promise.resolve(true);}}},{provide:ActivatedRoute,useValue:{}}]);
  const value=runInInjectionContext(injector,()=>new callback.module.AuthCallbackComponent());value.handleNavigation({role:'OWNER'});assert.deepEqual(calls[0],[target]);injector.destroy();
 }
});
test('owners with blocked training entitlement can still reach billing after login',()=>{
 const navigation=[];const injector=createEnvironmentInjector([{provide:UiFeedbackService,useValue:feedbackStub},{provide:AuthService,useValue:{setParlours:()=>{},setParlourName:()=>{},setActiveSport:()=>{},fetchAcademyContext:()=>throwError(()=>({status:403}))}},{provide:Router,useValue:{navigate:args=>{navigation.push(args);return Promise.resolve(true);}}},{provide:ActivatedRoute,useValue:{}}]);
 const value=runInInjectionContext(injector,()=>new callback.module.AuthCallbackComponent());value.handleNavigation({role:'OWNER'});assert.deepEqual(navigation[0],['/billing']);injector.destroy();
});
test('screens expose a mobile menu and capability-specific facility tabs without student placeholders',()=>{
 const template=fs.readFileSync(new URL('../src/app/training/training.component.html',import.meta.url),'utf8');assert.match(fs.readFileSync(new URL('../src/app/sidebar/sidebar.component.ts',import.meta.url),'utf8'),/aria-expanded/);assert.match(fs.readFileSync(new URL('../src/app/training/sport-configuration.component.ts',import.meta.url),'utf8'),/Save Changes/);assert.doesNotMatch(template,/Students|Attendance/);
});

 test('bulk registration omits already registered activities and starts unconfigured',()=>{
  let body;const {value,injector}=component({create:(path,data)=>{assert.equal(path,'sports/bulk');body=data;return of([cricket]);}});
  value.selectedCodes=new Set(['CRICKET','BADMINTON','YOGA']);value.registerSports();assert.deepEqual(body.activities.map(a=>a.catalogueCode),['YOGA']);assert.equal('training' in body.activities[0],false);injector.destroy();
 });
 test('catalogue categorization preserves every item and search works across groups',()=>{
  const {value,injector}=component();value.catalogue=[{code:'TENNIS',label:'Tennis'},{code:'KARATE',label:'Karate'},{code:'UNUSUAL',label:'Unusual activity'}];
  assert.equal(value.catalogueGroups.flatMap(g=>g.items).length,3);value.categoryFilter='Combat sports';assert.deepEqual(value.filteredCatalogue.map(x=>x.code),['KARATE']);value.categoryFilter='All activities';value.catalogueSearch='unusual';assert.equal(value.filteredCatalogue.length,1);injector.destroy();
 });
 test('staff role filtering searches the full membership list and retains status constraints',()=>{
  const {value,injector}=component({all:path=>of(path==='staff'?[coach,{...coach,id:4,role:'STAFF'},{...coach,id:5,active:false}]:[])},'staff');
  value.roleFilter='COACH';value.statusFilter='true';value.filter();assert.deepEqual(value.rows.map(m=>m.id),[3]);assert.equal(value.pageLoading,false);injector.destroy();
 });
 test('drawers are opened by explicit actions and can be closed without changing saved records',()=>{
  const {value,injector}=component();assert.equal(value.staffDrawer,false);value.newStaff();assert.equal(value.staffDrawer,true);value.closeStaff();assert.equal(value.staffDrawer,false);value.newBatch();assert.equal(value.batchDrawer,true);value.closeBatch();assert.equal(value.batchDrawer,false);injector.destroy();
 });

test('activity deactivation waits for confirmation and preserves optimistic version',()=>{
 let calls=0,body;const {value,injector}=component({update:(_path,data)=>{calls++;body=data;return of({...cricket,version:1,active:false});}});
 value.requestSportStatus(cricket);assert.equal(calls,0);assert.equal(value.confirmationSport.id,cricket.id);value.confirmSportStatus();assert.equal(calls,1);assert.equal(body.version,cricket.version);assert.equal(body.active,false);assert.equal(value.confirmationSport,null);injector.destroy();
});
test('an activity may register with neither capability enabled',()=>{
 const {value,injector}=component();value.catalogue=[{code:'YOGA',label:'Yoga'}];value.selectedCodes.add('YOGA');assert.equal(value.validSelection,true);value.optionsFor('YOGA').training=false;assert.equal(value.validSelection,true);injector.destroy();
});

test('registered catalogue activities are not selected or reactivated from the catalogue',()=>{
 const {value,injector}=component();value.selectActivity({code:'CRICKET',label:'Cricket'});assert.equal(value.selectedSport,null);assert.equal(value.selectedCodes.size,0);injector.destroy();
});
test('sport icons identify known activities and use neutral initials for others',()=>{
 const {value,injector}=component();assert.equal(value.sportIcon({code:'CRICKET',label:'Cricket'}),'🏏');assert.equal(value.sportIcon({code:'BASKETBALL',label:'Basketball'}),'🏀');assert.equal(value.sportIcon({code:'KABADDI',label:'Kabaddi'}),'K');injector.destroy();
});

test('explicit login validates availability then sends one combined registration with requested roles',()=>{
 const calls=[];const {value,injector}=component({create:(path,body)=>{calls.push({path,body});return of(path==='staff/login-availability'?{available:true}:{...coach,appUserId:44,notificationState:'SENT'});}},'staff');
 value.loginAtRegistration=true;value.staffForm.name='Coach';value.staffForm.email='coach@example.test';value.loginForm.username='coach';value.loginRoles=new Set(['MANAGER','INSTRUCTOR']);value.saveStaff();
 assert.deepEqual(calls.map(c=>c.path),['staff/login-availability','staff/registration']);assert.deepEqual(calls[1].body.roles,['MANAGER','INSTRUCTOR']);assert.equal(calls[0].body.requestId,calls[1].body.requestId);assert.match(calls[1].body.requestId,/^[0-9a-f-]{36}$/);assert.equal(value.editingStaff.appUserId,44);injector.destroy();
});
test('designation defaults and lifecycle labels stay separate from employment',()=>{
 const {value,injector}=component({},'staff');value.staffForm.designation='MANAGER';value.defaultLoginRole();assert.deepEqual([...value.loginRoles],['MANAGER']);
 for(const [loginAccessStatus,label] of [['INVITATION_PENDING','Invitation Pending'],['INVITATION_EXPIRED','Invitation Expired'],['ACTIVE','Active'],['DISABLED','Disabled']])assert.equal(value.loginStatus({...coach,active:false,loginAccessStatus}),label);
 injector.destroy();
});
test('login disable request retains staff and roles remain a separate versioned API',()=>{
 const calls=[];const {value,injector}=component({create:(path,body)=>{calls.push({path,body});return of({...coach,appUserId:10,loginState:'DISABLED'});},update:(path,body)=>{calls.push({path,body});return of(coach);}},'staff');
 value.disableTarget=coach;value.loginAction(coach,'disable');assert.equal(calls[0].path,'staff/3/login/disable');assert.deepEqual(calls[0].body,{version:1});assert.equal(value.disableTarget,null);
 value.loginTarget=coach;value.loginRoles=new Set(['MANAGER','INSTRUCTOR']);value.saveRoles();assert.equal(calls[1].path,'staff/3/login/roles');assert.deepEqual(calls[1].body.roles,['MANAGER','INSTRUCTOR']);injector.destroy();
});

test('non-owner callback opens current dashboard for academy and club contexts',()=>{
 for(const [eligible,target] of [[true,'/academy/dashboard'],[false,'/academy/dashboard']]){
  const calls=[];const injector=createEnvironmentInjector([{provide:UiFeedbackService,useValue:feedbackStub},{provide:AuthService,useValue:{setParlours:()=>{},setParlourName:()=>{},setActiveSport:()=>{},fetchAcademyContext:()=>of({eligible})}},{provide:Router,useValue:{navigate:args=>{calls.push(args);return Promise.resolve(true);}}},{provide:ActivatedRoute,useValue:{}}]);
  const value=runInInjectionContext(injector,()=>new callback.module.AuthCallbackComponent());value.handleNavigation({role:'STAFF'});assert.deepEqual(calls[0],[target]);injector.destroy();
 }
});

const sidebarSessionURL='data:text/javascript;base64,'+Buffer.from('export class RunningSessionsService {}').toString('base64');
const {RunningSessionsService:SidebarSessionService}=await import(sidebarSessionURL);
const sidebarModule=await load('../src/app/sidebar/sidebar.component.ts',{'../ui/ui-feedback.service':feedbackURL,'../services/auth':authURL,'../api-error':errors.url,'../sessions/running-sessions.service':sidebarSessionURL});
test('workspace navigation respects academy roles and keeps club/admin destinations valid',()=>{
 for(const [role,eligible,target,manage] of [['OWNER',true,'/academy/dashboard',true],['MANAGER',true,'/academy/dashboard',true],['STAFF',true,'/academy/dashboard',false],['OWNER',false,'/academy/dashboard',false],['ADMIN',false,'/admin',false]]){
  const injector=createEnvironmentInjector([{provide:UiFeedbackService,useValue:feedbackStub},{provide:SidebarSessionService,useValue:{}},{provide:AuthService,useValue:{hasRole:r=>r===role,academyContextValue:{eligible}}},{provide:Router,useValue:{isActive:()=>false}},{provide:errors.module.ApiNotifications,useValue:{error:{set:()=>{}}}}]);
  const value=runInInjectionContext(injector,()=>new sidebarModule.module.SidebarComponent());
  assert.equal(value.dashboardTarget,target);assert.equal(value.canManage,manage);injector.destroy();
 }
});

test('club workspace Dashboard click uses workspace access without enabling academy operations',()=>{
 const calls=[];const context={eligible:false,workspaceEligible:true};
 const injector=createEnvironmentInjector([{provide:UiFeedbackService,useValue:feedbackStub},{provide:SidebarSessionService,useValue:{}},{provide:AuthService,useValue:{hasRole:r=>r==='OWNER',academyContextValue:context,fetchAcademyContext:()=>of(context)}},{provide:Router,useValue:{isActive:()=>false,navigate:args=>{calls.push(args);return Promise.resolve(true);}}},{provide:errors.module.ApiNotifications,useValue:{error:{set:()=>{}}}}]);
 const value=runInInjectionContext(injector,()=>new sidebarModule.module.SidebarComponent());
 assert.equal(value.dashboardTarget,'/academy/dashboard');assert.equal(value.canManage,false);
 value.navigateDashboard({preventDefault:()=>{}});assert.deepEqual(calls,[['/academy/dashboard']]);injector.destroy();
});

const academyModule=await load('../src/app/academy/academy.component.ts',{'../ui/ui-feedback.service':feedbackURL,'../services/auth':authURL,'../api-error':errors.url,'../ui/shared':shared.url});
function dashboard(role,response){
 const requests=[];
 const injector=createEnvironmentInjector([{provide:UiFeedbackService,useValue:feedbackStub},{provide:AuthService,useValue:{hasRole:r=>r===role}},{provide:HttpClient,useValue:{get:path=>{requests.push(path);return response;}}},{provide:ActivatedRoute,useValue:{data:of({page:'dashboard'})}}]);
 const value=runInInjectionContext(injector,()=>new academyModule.module.AcademyComponent());value.ngOnInit();return {value,requests,injector};
}
const setupStep=(key,path)=>({key,label:key,applicable:true,complete:false,path});
test('dashboard uses authoritative completion and avoids operational requests',()=>{
 for(const [percentage,status] of [[100,'READY'],[67,'INCOMPLETE']]){
  const {value,requests,injector}=dashboard('OWNER',of({percentage,status,steps:[setupStep('SPORTS','/academy/sports')]}));
  assert.equal(value.progress.percentage,percentage);assert.equal(value.setupCompleted,percentage===100);assert.deepEqual(requests,['/api/academy/setup']);assert.equal(value.loading,false);injector.destroy();
 }
});
test('dashboard clears stale success on errors or missing/inconsistent completion data',()=>{
 for(const response of [throwError(()=>({status:503})),of(null),of({percentage:null,status:'READY'}),of({percentage:100,status:'INCOMPLETE'}),of({percentage:NaN,status:'READY'})]){
  const {value,injector}=dashboard('OWNER',response);assert.equal(value.progress,null);assert.equal(value.setupCompleted,false);assert.ok(value.error);assert.equal(value.loading,false);injector.destroy();
 }
});
test('manager continuation avoids owner-only profile and review steps; staff cannot fetch setup',()=>{
 const {value,injector}=dashboard('MANAGER',of({percentage:50,status:'INCOMPLETE',steps:[setupStep('PROFILE','/academy/setup'),setupStep('SPORTS','/academy/sports'),setupStep('REVIEW','/academy/setup')]}));
 assert.equal(value.dashboardContinuePath,'/academy/sports');value.progress.steps=value.progress.steps.filter(s=>s.key!=='SPORTS');assert.equal(value.dashboardContinuePath,null);injector.destroy();
 for(const role of ['STAFF','INSTRUCTOR']){const {value,requests,injector}=dashboard(role,of({percentage:100,status:'READY'}));assert.deepEqual(requests,[]);assert.equal(value.progress,null);assert.equal(value.dashboardContinuePath,null);injector.destroy();}
});
test('dashboard hides prior completion while refreshing and after a failed refresh',()=>{
 const pending=new Subject();const {value,injector}=dashboard('OWNER',pending);
 assert.equal(value.loading,true);assert.equal(value.progress,null);
 pending.next({percentage:100,status:'READY',steps:[]});assert.equal(value.setupCompleted,true);
 value.load();assert.equal(value.loading,true);assert.equal(value.progress,null);
 pending.error({status:503});assert.equal(value.loading,false);assert.equal(value.setupCompleted,false);assert.ok(value.error);injector.destroy();
});
const guards=await load('../src/app/services/workspace-guards.ts',{'./auth':authURL,'../sessions/running-sessions.service':'data:text/javascript;base64,'+Buffer.from('export class RunningSessionsService {}').toString('base64')});
test('role navigation and direct route guards deny staff/coach administration and standard manager profile access',()=>{
 for(const [role,profile,setup] of [['OWNER',true,true],['MANAGER',true,true],['MANAGER',false,true],['STAFF',false,false],['INSTRUCTOR',false,false],['COACH',false,false]]){
  let brandingCalls=0;
  const injector=createEnvironmentInjector([{provide:UiFeedbackService,useValue:feedbackStub},{provide:SidebarSessionService,useValue:{}},{provide:AuthService,useValue:{hasRole:r=>r===role,academyContextValue:{eligible:true},businessBrandingValue:{permissions:{viewProfile:profile}},fetchBusinessBranding:()=>{brandingCalls++;return of({permissions:{viewProfile:profile}});}}},{provide:Router,useValue:{createUrlTree:args=>args[0],isActive:()=>false}},{provide:errors.module.ApiNotifications,useValue:{error:{set:()=>{}}}}]);
  const sidebar=runInInjectionContext(injector,()=>new sidebarModule.module.SidebarComponent());
  assert.equal(sidebar.canManageStaff,setup);assert.equal(sidebar.canViewProfile,profile);assert.equal(sidebar.dashboardTarget,'/academy/dashboard');
  assert.equal(runInInjectionContext(injector,guards.module.setupManagementGuard),setup?true:'/academy/dashboard');
  runInInjectionContext(injector,guards.module.businessProfileGuard).subscribe(result=>assert.equal(result,profile?true:'/academy/dashboard'));
  assert.equal(brandingCalls,setup?1:0);injector.destroy();
 }
});
test('profile guard fails closed when permission refresh fails',()=>{const injector=createEnvironmentInjector([{provide:UiFeedbackService,useValue:feedbackStub},{provide:AuthService,useValue:{hasRole:r=>r==='MANAGER',fetchBusinessBranding:()=>throwError(()=>({status:503}))}},{provide:Router,useValue:{createUrlTree:args=>args[0]}}]);runInInjectionContext(injector,guards.module.businessProfileGuard).subscribe(result=>assert.equal(result,'/academy/dashboard'));injector.destroy();});

const homeModule=await load('../src/app/home/home.component.ts',{'../ui/ui-feedback.service':feedbackURL,'../services/auth':authURL});
test('authenticated home opens current Dashboard without requesting legacy parlours or context',()=>{
 const calls=[];
 const injector=createEnvironmentInjector([{provide:UiFeedbackService,useValue:feedbackStub},{provide:AuthService,useValue:{hasRole:()=>false,isAuthenticated:()=>true}},{provide:HttpClient,useValue:{get:()=>{throw new Error('Home must not request legacy APIs');}}},{provide:Router,useValue:{navigate:args=>{calls.push(args);return Promise.resolve(true);}}}]);
 const value=runInInjectionContext(injector,()=>new homeModule.module.HomeComponent());
 value.ngOnInit();assert.deepEqual(calls,[['/academy/dashboard']]);value.ngOnDestroy();injector.destroy();
});

test('duplicate login availability never persists staff and keeps form data for correction',()=>{
 const calls=[];const {value,injector}=component({create:(path,body)=>{calls.push({path,body});return throwError(()=>({status:409,error:{errorCode:'DUPLICATE_USERNAME'}}));}},'staff');value.loginAtRegistration=true;value.staffForm.name='Coach';value.staffForm.email='coach@example.test';value.loginForm.username='coach';value.saveStaff();assert.equal(calls.length,1);assert.equal(calls[0].path,'staff/login-availability');assert.match(value.staffFieldErrors.username,/already in use/);assert.equal(value.staffForm.name,'Coach');assert.equal(value.editingStaff,null);value.credentialsChanged('username');assert.equal(value.staffFieldErrors.username,undefined);injector.destroy();
});
test('failed combined registration preserves the request key and entered data on retry',()=>{
 const calls=[];let failed=true;const {value,injector}=component({create:(path,body)=>{calls.push({path,body});if(path==='staff/login-availability')return of({available:true});return failed?throwError(()=>({status:503,error:{errorCode:'STAFF_REGISTRATION_UNCERTAIN'}})):of({...coach,appUserId:90,notificationState:'SENT'});}},'staff');value.loginAtRegistration=true;value.staffForm.name='Coach';value.staffForm.email='coach@example.test';value.loginForm.username='coach';value.saveStaff();assert.equal(value.staffForm.name,'Coach');assert.equal(value.editingStaff,null);failed=false;value.saveStaff();const attempts=calls.filter(c=>c.path==='staff/registration');assert.equal(attempts.length,2);assert.equal(attempts[0].body.requestId,attempts[1].body.requestId);assert.equal(value.editingStaff.appUserId,90);injector.destroy();
});
test('pending availability prevents duplicate submits and stale validation cannot overwrite corrections',()=>{
 const pending=new Subject();let calls=0;const {value,injector}=component({create:()=>{calls++;return calls===1?pending:of({available:true});}},'staff');value.loginAtRegistration=true;value.staffForm.name='Coach';value.staffForm.email='coach@example.test';value.loginForm.username='coach';value.saveStaff();value.saveStaff();assert.equal(calls,1);assert.equal(value.checkingLogin,true);value.loginForm.username='new.coach';value.credentialsChanged('username');pending.error({status:409,error:{errorCode:'DUPLICATE_USERNAME'}});assert.equal(value.staffFieldErrors.username,undefined);value.checkLoginAvailability();assert.equal(calls,2);assert.equal(value.checkingLogin,false);injector.destroy();
});


test('closing staff editor during preflight cancels the continuation before creation',()=>{
 const pending=new Subject();const calls=[];const {value,injector}=component({create:(path)=>{calls.push(path);return pending;}},'staff');value.staffDrawer=true;value.loginAtRegistration=true;value.staffForm.name='Coach';value.staffForm.email='coach@example.test';value.loginForm.username='coach';value.saveStaff();assert.equal(value.checkingLogin,true);value.closeStaff();pending.next({available:true});pending.complete();assert.equal(value.staffDrawer,false);assert.equal(value.checkingLogin,false);assert.deepEqual(calls,['staff/login-availability']);assert.equal(value.editingStaff,null);injector.destroy();
});
