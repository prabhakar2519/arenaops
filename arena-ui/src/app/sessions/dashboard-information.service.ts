import { Injectable, computed, inject, signal } from '@angular/core';
import { Subscription, catchError, forkJoin, of, switchMap } from 'rxjs';
import { AuthService } from '../services/auth';
import { TrainingBatch, TrainingService } from '../training/training.service';
import { AcademyOperationsService, CollectionSummary, SessionStudent } from '../students/academy-operations.service';
import { RunningDashboard, RunningSessionsService } from './running-sessions.service';

export interface OperationalAlert {count:number;revision?:string;id:string;title:string;description:string;path:string;query?:Record<string,string>;}
export interface TrainingSummary {date:string;activeBatches:number;recordedSessions:number;expectedEntries:number;recordedEntries:number;provisional:number;attendanceAvailable:boolean;collections:CollectionSummary|null;missingCoaches:number;}
@Injectable({providedIn:'root'})
export class DashboardInformationService {
  private readonly auth=inject(AuthService);
  private readonly sessions=inject(RunningSessionsService);
  private readonly trainingApi=inject(TrainingService);
  private readonly operations=inject(AcademyOperationsService);
  readonly facility=signal<RunningDashboard|null>(null);
  readonly approvals=signal(0);private readonly approvalRevision=signal('');
  readonly training=signal<TrainingSummary|null>(null);
  readonly loading=signal(false);readonly error=signal('');readonly facilityError=signal('');
  private trainingRequest?:Subscription;private facilityRequest?:Subscription;private approvalsRequest?:Subscription;
  private lastTraining=0;private lastFacility=0;
  get canManage():boolean {return this.auth.hasRole('OWNER')||this.auth.hasRole('MANAGER');}
  get trainingApplicable():boolean {return this.canManage && !!this.auth.academyContextValue?.eligible;}
  private localTime(time=Date.now()):string {return new Intl.DateTimeFormat('sv-SE',{timeZone:this.auth.academyContextValue?.timezone||'Asia/Kolkata',year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',second:'2-digit',hourCycle:'h23'}).format(time).replace(' ','T');}
  readonly alerts=computed<OperationalAlert[]>(()=>{
    if(!this.canManage)return [];
    const alerts:OperationalAlert[]=[];const data=this.facility();const training=this.training();
    if(data){
      const unsettled=data.unsettled || data.recent.filter(s=>s.status==='UNCONFIRMED'||Number(s.outstandingAmount)>0);
      if(unsettled.length)alerts.push({id:'unsettled',count:unsettled.length,revision:unsettled.map(s=>s.id).sort().join(','),title:`${unsettled.length} unsettled facility session${unsettled.length===1?'':'s'}`,description:'Review pending balances and record collections.',path:'/academy/dashboard',query:{view:'UNSETTLED'}});
      const unconfigured=data.facilities.filter(f=>f.active&&!f.configured);
      if(unconfigured.length)alerts.push({id:'configuration',count:unconfigured.length,revision:unconfigured.map(f=>f.id).sort().join(','),title:`${unconfigured.length} facilities need configuration`,description:'Complete facility types and pricing before starting sessions.',path:'/academy/facilities'});
      const now=this.localTime(),soon=this.localTime(Date.now()+30*60000);
      const upcoming=(data.reserved||[]).filter(s=>!!s.reservedStart&&!!s.reservedEnd&&s.reservedStart<=soon&&s.reservedEnd>now);
      if(upcoming.length)alerts.push({id:'reservations',count:upcoming.length,revision:upcoming.map(s=>s.id).sort().join(','),title:`${upcoming.length} reservations starting soon or ready`,description:'Review reserved time slots and facility availability.',path:'/academy/dashboard',query:{view:'RESERVED'}});
    }
    if(training){
      if(training.provisional)alerts.push({id:'attendance-confirmation',count:training.provisional,title:`${training.provisional} attendance entries awaiting confirmation`,description:'Confirm provisional check-ins in Attendance.',path:'/academy/attendance'});
      if(training.missingCoaches)alerts.push({id:'coaches',count:training.missingCoaches,title:`${training.missingCoaches} active batches without a coach`,description:'Review coach assignments in Training & Batches.',path:'/academy/batches'});
      if(training.collections?.overdueCharges)alerts.push({id:'student-dues',count:training.collections.overdueCharges,title:`${training.collections.overdueCharges} overdue student charges`,description:'Review outstanding student fees in Student Collections.',path:'/academy/collections'});
    }
    if(this.approvals())alerts.push({id:'discount-approvals',count:this.approvals(),revision:this.approvalRevision(),title:`${this.approvals()} discount approvals pending`,description:'Review requested discounts. Unapproved amounts remain outstanding.',path:'/academy/dashboard',query:{attention:'discount-approvals'}});
    return alerts;
  });
  // Read state is presentation-only, scoped to this signed-in account in this browser.
  // It never removes an operational alert or authorizes access to its underlying records.
  private readonly readAlerts=signal<Record<string,string>>({});
  private readScope='';
  readonly attentionCount=computed(()=>this.alerts().reduce((total,alert)=>total+alert.count,0));
  readonly unreadAlerts=computed(()=>this.alerts().filter(alert=>this.readAlerts()[alert.id]!==this.alertRevision(alert)));
  notificationLabel():string {const n=this.unreadAlerts().length;return `Operational notifications, ${n} unread notification${n===1?'':'s'}`;}
  isUnread(alert:OperationalAlert):boolean{return this.readAlerts()[alert.id]!==this.alertRevision(alert);}
  private alertRevision(alert:OperationalAlert):string{return `${alert.count}:${alert.revision||''}`;}
  private restoreReadState():void {
    const username=this.auth.currentUserValue?.username;
    const scope=username?`arenaops-operational-read-v1:${username.toLowerCase()}`:'';
    if(scope===this.readScope)return;this.readScope=scope;this.readAlerts.set({});
    if(scope)try{const saved=JSON.parse(localStorage.getItem(scope)||'{}');if(saved&&typeof saved==='object'&&!Array.isArray(saved)){const safe:Record<string,string>={};for(const id of ['unsettled','configuration','reservations','attendance-confirmation','coaches','student-dues','discount-approvals'])if(typeof saved[id]==='string'&&saved[id].length<10000)safe[id]=saved[id];this.readAlerts.set(safe);}}catch{ /* Storage is optional; in-memory state remains usable. */ }
  }
  private persistReadState():void {if(this.readScope)try{localStorage.setItem(this.readScope,JSON.stringify(this.readAlerts()));}catch{ /* Private browsing/storage limits must not block operations. */ }}
  markRead(alert:OperationalAlert):void {this.restoreReadState();this.readAlerts.update(read=>({...read,[alert.id]:this.alertRevision(alert)}));this.persistReadState();}
  markAllRead():void {for(const alert of this.alerts())this.markRead(alert);}
  private reconcileReadState(categories:string[]):void {
    this.restoreReadState();const present=new Set(this.alerts().map(alert=>alert.id));
    this.readAlerts.update(read=>{const next={...read};for(const id of categories)if(!present.has(id))delete next[id];return next;});this.persistReadState();
  }
  setFacilitySnapshot(data:RunningDashboard):void {if(!this.canManage)return;this.facility.set(data);this.facilityError.set('');this.lastFacility=Date.now();this.reconcileReadState(['unsettled','configuration','reservations']);this.approvalsRequest?.unsubscribe();this.approvalsRequest=this.sessions.inbox().subscribe({next:box=>{this.approvalRevision.set(box.requests.map(r=>r.id).sort().join(','));this.approvals.set(box.canApprove?box.requests.length:0);this.reconcileReadState(['discount-approvals']);},error:failure=>{if([401,403].includes(failure.status)){this.approvals.set(0);this.approvalRevision.set('');}/* Keep last confirmed pending state only for transient failures. */}});this.refreshTraining();}
  reset():void {this.approvalsRequest?.unsubscribe();this.trainingRequest?.unsubscribe();this.facilityRequest?.unsubscribe();this.facility.set(null);this.approvals.set(0);this.approvalRevision.set('');this.training.set(null);this.loading.set(false);this.error.set('');this.facilityError.set('');this.lastTraining=0;this.lastFacility=0;this.readScope='';this.readAlerts.set({});}
  refresh():void {
    if(!this.canManage){this.reset();return;}
    if(Date.now()-this.lastFacility>30000||!this.facility()){this.facilityRequest?.unsubscribe();this.facilityRequest=this.sessions.dashboard().subscribe({next:data=>this.setFacilitySnapshot(data),error:()=>this.facilityError.set('Facility alerts could not be refreshed. Open Dashboard to retry.')});}
    this.refreshTraining();
  }
  refreshTraining(force=false):void {
    if(!this.trainingApplicable){this.trainingRequest?.unsubscribe();this.training.set(null);this.error.set('');this.loading.set(false);return;}
    if(!force&&(this.loading()||Date.now()-this.lastTraining<30000))return;
    this.trainingRequest?.unsubscribe();this.loading.set(true);this.error.set('');
    const date=this.localTime().slice(0,10);let collectionDenied=false,attendanceDenied=false;
    this.trainingRequest=forkJoin({batches:this.trainingApi.all<TrainingBatch>('batches'),sessions:this.operations.sessions(date),collections:this.operations.summary().pipe(catchError(failure=>{collectionDenied=[401,403].includes(failure.status);return of(null);}))}).pipe(switchMap(result=>{
      const requests=result.sessions.filter(s=>s.status!=='CANCELLED').map(s=>this.operations.sessionStudents(s.id).pipe(catchError(failure=>{attendanceDenied ||= [401,403].includes(failure.status);return of(null);})));
      return (requests.length?forkJoin(requests):of([] as (SessionStudent[]|null)[])).pipe(switchMap(rosters=>of({result,rosters})));
    })).subscribe({next:({result,rosters})=>{
      const rows=rosters.flatMap(r=>r||[]);const active=result.batches.filter(b=>b.status==='ACTIVE');
      this.training.set({date,activeBatches:active.length,recordedSessions:result.sessions.filter(s=>s.status!=='CANCELLED').length,expectedEntries:rows.length,recordedEntries:rows.filter(r=>r.status!=='NOT_RECORDED').length,provisional:Math.max(rows.filter(r=>r.status==='PROVISIONAL').length,!attendanceDenied&&rosters.some(r=>r===null)&&this.training()?.date===date?(this.training()?.provisional||0):0),attendanceAvailable:rosters.every(r=>r!==null),collections:result.collections||(!collectionDenied?this.training()?.collections:null)||null,missingCoaches:active.filter(b=>!b.coachIds?.length).length});
      this.reconcileReadState(['coaches',...(rosters.every(r=>r!==null)?['attendance-confirmation']:[]),...(result.collections?['student-dues']:[])]);this.lastTraining=Date.now();this.loading.set(false);
    },error:failure=>{if([401,403].includes(failure.status))this.training.set(null);this.loading.set(false);this.error.set('Training and attendance summaries could not be loaded.');}});
  }
}
