import {facilityCheckinUrl} from '../checkin/facility-qr.utils';
import {MVP_FEATURES} from '../features';
import {BarcodeFormat,QRCodeWriter} from '@zxing/library';
import { CommonModule } from '@angular/common';
import { Component, ElementRef, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { DashboardInformationService } from './dashboard-information.service';
import { interval, Subscription } from 'rxjs';
import { apiError } from '../api-error';
import { AuthService } from '../services/auth';
import { RunningDashboard, RunningSession, RunningSessionsService, SessionFacility,CustomerCheckin,FacilityQr,StopQuote,DiscountInbox,DiscountRequest } from './running-sessions.service';

@Component({ selector: 'app-running-dashboard', standalone: true, imports: [CommonModule, FormsModule, RouterLink], templateUrl: './running-dashboard.component.html', styleUrls: ['./running-dashboard.component.scss','./dashboard-structure.scss'] })
export class RunningDashboardComponent implements OnInit, OnDestroy {
readonly features=MVP_FEATURES;  private readonly service = inject(RunningSessionsService);
  readonly auth = inject(AuthService);
  readonly information = inject(DashboardInformationService);
  private readonly route = inject(ActivatedRoute);
  private viewSubscription?: Subscription;
  private poll?: Subscription;
  private checkinPoll?: Subscription;
  private checkinRequest?: Subscription;
  private checkinLoading=false;
  private ticker?: Subscription;
  qrError='';qrNeedsSetup=false;qrFacility:SessionFacility|null=null;
  pendingCheckins:CustomerCheckin[]=[];checkinError='';qr:FacilityQr|null=null;qrImage='';qrBusy=false;checkinTarget:CustomerCheckin|null=null;checkinEnd='';
  loadCheckins():void {if(this.checkinLoading)return;this.checkinLoading=true;this.checkinRequest=this.service.checkins().subscribe({next:rows=>{this.pendingCheckins=rows;this.checkinError='';},error:()=>{this.checkinLoading=false;this.checkinError='Customer check-ins could not be refreshed. Retry before starting.';},complete:()=>{this.checkinLoading=false;}});}
  startCheckinPolling(period=2000):void {this.checkinPoll?.unsubscribe();this.loadCheckins();this.checkinPoll=interval(period).subscribe(()=>this.loadCheckins());}
  customerReady(id:number):boolean{return this.pendingCheckins.some(c=>c.facilityId===id&&new Date(c.expiresAt).getTime()>this.now);}
  checkinDisplayUrl(url:string,origin=window.location.origin,env=window.ARENAOPS_CONFIG?.arenaEnv):string {
    return facilityCheckinUrl(url,origin,env);
  }
  showQr(facility:SessionFacility):void {if(this.features.snookerFacilitiesOnly&&facility.sport!=='SNOOKER')return;if(this.qrBusy)return;this.qrBusy=true;this.qrError='';this.qrNeedsSetup=false;this.qrFacility=facility;this.service.issueCheckinQr(facility.id).subscribe({next:q=>{try{const url=this.checkinDisplayUrl(q.url);q={...q,url};const matrix=new QRCodeWriter().encode(url,BarcodeFormat.QR_CODE,280,280,new Map());const canvas=document.createElement('canvas');canvas.width=matrix.getWidth();canvas.height=matrix.getHeight();const ctx=canvas.getContext('2d');if(!ctx)throw Error();const data=ctx.createImageData(canvas.width,canvas.height);for(let y=0;y<canvas.height;y++)for(let x=0;x<canvas.width;x++){const i=(y*canvas.width+x)*4,v=matrix.get(x,y)?0:255;data.data[i]=v;data.data[i+1]=v;data.data[i+2]=v;data.data[i+3]=255;}ctx.putImageData(data,0,0);this.qrImage=canvas.toDataURL('image/png');this.qr=q;}catch{this.qrError='QR display is unavailable. Quick Start is still available.';}this.qrBusy=false;},error:e=>{this.qrBusy=false;const error=apiError(e);this.qrNeedsSetup=error.errorCode==='PUBLIC_SITE_NOT_PUBLISHED';this.qrError=this.qrNeedsSetup&&!this.features.publicClubWebsite?'QR check-in is unavailable for this club. Manual Start session remains available.':error.description;}});}
  openCheckin(c:CustomerCheckin):void {this.checkinTarget=c;this.checkinEnd=this.academyTime(Date.now()+3600000);this.checkinError='';}
  confirmCheckin():void {if(!this.checkinTarget||this.busy)return;this.busy=true;this.service.confirmCheckin(this.checkinTarget.id,this.checkinEnd||undefined).subscribe({next:()=>{this.busy=false;this.checkinTarget=null;this.message='Customer session started.';this.activeTab='RUNNING';this.refresh();},error:e=>{this.busy=false;this.checkinError=e.status===409?'This table is running or reserved for that time. Refresh and choose another time, or use another facility.':apiError(e).description;}});}
  cancelCheckin(c:CustomerCheckin):void {if(this.busy)return;this.busy=true;this.service.cancelCheckin(c.id).subscribe({next:()=>{this.busy=false;this.loadCheckins();},error:e=>{this.busy=false;this.checkinError=apiError(e).description;}});}
  data: RunningDashboard | null = null;
  loading = true; busy = false; error = ''; message = '';
  errorCorrelationId?: string;
  now = Date.now();
  startTarget: SessionFacility | null = null;
  playerName = '';
  playerMobile = '';
  reservationMode = false;
  reservedStart = ''; reservedEnd = ''; plannedEndAt = '';
  get timezone(): string { return this.auth.academyContextValue?.timezone || 'Asia/Kolkata'; }
  private academyTime(time: number): string {
    const parts=new Intl.DateTimeFormat('en-CA',{timeZone:this.timezone,year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hourCycle:'h23'}).formatToParts(time);
    const part=(type:string)=>parts.find(p=>p.type===type)?.value;return `${part('year')}-${part('month')}-${part('day')}T${part('hour')}:${part('minute')}`;
  }
  canStartReserved(session: RunningSession): boolean { const current=this.academyTime(this.now);return !!session.reservedStart && !!session.reservedEnd && current>=session.reservedStart.slice(0,16) && current<session.reservedEnd.slice(0,16); }
  get bookableFacilities(): SessionFacility[] { return (this.data?.facilities || []).filter(f=>(!this.features.snookerFacilitiesOnly||f.sport==='SNOOKER')&&f.active && f.configured && Number(f.hourlyRate)>0); }
  openReservation(): void { const facility=this.bookableFacilities[0]; if(facility)this.openStart(facility,true); }
  stopTarget: RunningSession | null = null;
  paidAmount = '';
  stopQuote:StopQuote|null=null;quoteLoading=false;discountAmount=0;discountReason='';private quoteGeneration=0;
  inbox:DiscountInbox={canApprove:false,requests:[]};decisionTarget:DiscountRequest|null=null;decisionApprove=true;decisionReason='';
  settlementDiscount=0;settlementDiscountReason='';
  get originalBill():number{return this.stopQuote && this.stopQuote.durationSeconds<60&&!this.quickStop?0:Number(this.stopQuote?.originalBill||0);}
  withinDiscountAuthority(amount:number,gross:number):boolean {const p=this.stopQuote?.authority||this.service.permissions();return !!p?.discountEnabled&&(p.maxPercent==null||amount*100<=gross*p.maxPercent)&&(p.maxAmount==null||amount<=p.maxAmount)&&(p.dailyRemaining==null||amount<=p.dailyRemaining);}
  get discountPending():boolean{return this.discountAmount>0&&!this.withinDiscountAuthority(this.discountAmount,this.originalBill);}
  get payable():number{return Math.max(0,this.originalBill-(this.discountPending?0:Number(this.discountAmount||0)));}
  get outstanding():number{return Math.max(0,this.payable-(this.markUnsettled||this.quickStop?0:Number(this.paidAmount||0)));}
  clearUnconfirmedCollection():void {if(this.markUnsettled||this.quickStop)this.paidAmount='0';}
  settlementStep(session:RunningSession):number{return this.isSnooker(session)&&Number.isInteger(Number(session.totalAmount))&&Number.isInteger(Number(session.paidAmount))?1:0.01;}
  quoteDuration():string {const n=this.stopQuote?.durationSeconds||0;return [Math.floor(n/3600),Math.floor(n%3600/60),n%60].map(v=>String(v).padStart(2,'0')).join(':');}
  loadInbox():void {this.service.inbox().subscribe({next:inbox=>{this.inbox=inbox;this.focusApprovalPanel();},error:()=>{this.inbox={canApprove:false,requests:[]};}});}
  decide(request:DiscountRequest,approve:boolean):void {this.decisionTarget=request;this.decisionApprove=approve;this.decisionReason='';}
  saveDecision():void {if(!this.decisionTarget||!this.decisionReason.trim()||this.busy)return;this.busy=true;this.service.decision(this.decisionTarget.id,this.decisionApprove,this.decisionReason.trim()).subscribe({next:()=>{this.busy=false;this.decisionTarget=null;this.message='Discount decision recorded.';this.refresh();},error:e=>{this.busy=false;this.error=apiError(e).description;}});}

  settleTarget: RunningSession | null = null;
  settlementAmount: string | number = ''; settlementError = '';
  private settlementReturnFocus: HTMLElement | null = null;
  @ViewChild('settlementDialog') set settlementDialog(ref: ElementRef<HTMLFormElement> | undefined) {
    if(ref)queueMicrotask(()=>{const input=ref.nativeElement.querySelector<HTMLInputElement>('input');input?.focus();input?.select();});
  }
  closeSettlement(): void {
    if(this.busy)return;
    this.settleTarget=null;this.settlementError='';
    if(this.settlementReturnFocus?.isConnected)this.settlementReturnFocus.focus();
  }
  trapSettlementFocus(event: Event): void {
    const keyboard=event as KeyboardEvent;
    const form=event.currentTarget as HTMLFormElement;
    const controls=Array.from(form.querySelectorAll<HTMLElement>('button:not([disabled]),input:not([disabled])'));
    const first=controls[0],last=controls[controls.length-1];
    if(keyboard.shiftKey&&document.activeElement===first){event.preventDefault();last?.focus();}
    else if(!keyboard.shiftKey&&document.activeElement===last){event.preventDefault();first?.focus();}
  }
  quickStop = false;
  markUnsettled = false;
  activeTab: 'RUNNING' | 'AVAILABLE' | 'RESERVED' | 'UNSETTLED' | 'HISTORY' | 'CHECKINS' = 'AVAILABLE';

  facilitySearch='';gameType='';selectedSport='';private focusDiscounts=false;
  get facilitySportLocked():boolean {return this.features.snookerFacilitiesOnly&&!!this.sportOptions.length&&(this.selectedSport?this.sportOptions.some(s=>String(s.sportId)===this.selectedSport&&s.sport!=='SNOOKER'):this.sportOptions.every(s=>s.sport!=='SNOOKER'));}
  get sportOptions(){return this.data?.facilitySummary||[];}
  sportChanged():void {if(this.gameType&&!this.gameTypes.includes(this.gameType))this.gameType='';}
  focusAttention(id:string):void {if(id!=='discount-approvals')return;this.focusDiscounts=true;this.focusApprovalPanel();}
  private focusApprovalPanel():void {if(!this.focusDiscounts||typeof requestAnimationFrame==='undefined')return;requestAnimationFrame(()=>{const panel=document.getElementById('discount-approvals');if(panel){panel.focus({preventScroll:true});panel.scrollIntoView({block:'center',behavior:'auto'});this.focusDiscounts=false;}});}
  matches(value:SessionFacility|RunningSession|CustomerCheckin):boolean {
    const item='facilityId' in value ? this.data?.facilities.find(f=>f.id===value.facilityId) : value as SessionFacility;
    const session=value as RunningSession;
    const name=[item?.code,item?.name,session.facilityCode,session.facilityName,(value as CustomerCheckin).facility].filter(Boolean).join(' ').toLowerCase();
    return (!this.features.snookerFacilitiesOnly||(item?.sport||session.sport)==='SNOOKER')&&(!this.selectedSport||String(item?.sportId||session.sportId)===this.selectedSport)&&(!this.facilitySearch.trim()||name.includes(this.facilitySearch.trim().toLowerCase()))&&(!this.gameType||(item?.facilityType||session.facilityType)===this.gameType);
  }
  filtered<T extends SessionFacility|RunningSession|CustomerCheckin>(rows:T[]):T[]{return rows.filter(v=>this.matches(v));}
  get gameTypes():string[]{return [...new Set((this.data?.facilities||[]).filter(f=>!this.selectedSport||String(f.sportId)===this.selectedSport).map(f=>f.facilityType))].sort();}
  get noFilterMatches():boolean {const rows=this.activeTab==='AVAILABLE'?this.available:this.activeTab==='RUNNING'?this.running:this.activeTab==='RESERVED'?this.reserved:this.activeTab==='UNSETTLED'?this.unsettled:this.activeTab==='HISTORY'?this.history:this.pendingCheckins;return rows.length>0&&!rows.some(v=>this.matches(v));}
  ngOnInit(): void {
    this.service.authority().subscribe({next:p=>this.service.permissions.set(p),error:()=>this.service.permissions.set(null)});
    this.viewSubscription=this.route.queryParamMap.subscribe(params=>{if(params.get('attention')==='discount-approvals')this.focusAttention('discount-approvals');const view=params.get('view');if(view && ['RUNNING','AVAILABLE','RESERVED','UNSETTLED','HISTORY','CHECKINS'].includes(view))this.activeTab=view as typeof this.activeTab;});
    this.refresh();
    this.startCheckinPolling();
    this.poll = interval(30000).subscribe(() => this.refresh(false));
    this.ticker = interval(1000).subscribe(() => this.now = Date.now());
  }
  ngOnDestroy(): void { this.checkinPoll?.unsubscribe();this.checkinRequest?.unsubscribe();this.viewSubscription?.unsubscribe(); this.poll?.unsubscribe(); this.ticker?.unsubscribe(); }
  refresh(showLoading = true): void {
    if (showLoading) this.loading = true;
    this.service.dashboard().subscribe({
      next: value => { this.data = value; this.information.setFacilitySnapshot(value);this.loadInbox();this.loadCheckins(); this.error = ''; this.errorCorrelationId = undefined; this.loading = false; },
      error: failure => {
        const error = apiError(failure);
        this.error = error.description;
        this.errorCorrelationId = error.correlationId;
        this.loading = false;
      }
    });
  }
  get businessName(): string { return this.auth.businessBrandingValue?.displayName || this.auth.academyContextValue?.name || 'Your sports workspace'; }
  get primarySport(): string { return this.data?.facilitySummary.length === 1 ? this.data.facilitySummary[0].sportName : 'Facilities'; }
  get heroImage(): string { return this.data?.facilitySummary[0]?.sport === 'BADMINTON' ? '/images/games/badminton/badminton-2.jpg' : '/snooker_legends_hero.png'; }
  sportIcon(sport: string): string { return sport.toUpperCase() === 'BADMINTON' ? '🏸' : sport.toUpperCase() === 'SNOOKER' ? '🎱' : '◈'; }
  get running(): RunningSession[] { return this.data?.running || []; }
  get available(): SessionFacility[] { return (this.data?.facilities || []).filter(facility => facility.available); }
  get reserved(): RunningSession[] { return this.data?.reserved || []; }
  reservationsFor(facilityId:number): RunningSession[] { return this.reserved.filter(s=>s.facilityId===facilityId).sort((a,b)=>(a.reservedStart || '').localeCompare(b.reservedStart || '')); }
  get unsettled(): RunningSession[] { return this.data?.unsettled || (this.data?.recent || []).filter(session => session.status === 'UNCONFIRMED' || (session.status === 'STOPPED' && Number(session.outstandingAmount) > 0)).slice(0,20); }
  get history(): RunningSession[] { return (this.data?.recent || []).filter(session => session.status !== 'UNCONFIRMED' && !(session.status === 'STOPPED' && Number(session.outstandingAmount) > 0)).slice(0,20); }
  get canStartSessions(): boolean { return this.available.length > 0; }
  openStart(facility: SessionFacility, reserve = false): void { if(this.features.snookerFacilitiesOnly&&facility.sport!=='SNOOKER')return; this.reservationMode = reserve; this.startTarget = facility; this.playerName = '';this.playerMobile=''; this.error = '';this.reservedStart=this.academyTime(Date.now()+15*60000);this.reservedEnd=this.academyTime(Date.now()+75*60000);this.plannedEndAt=this.academyTime(Date.now()+60*60000); }
  start(): void {
    if (!this.startTarget || this.busy) return;
    if(this.reservationMode && (!this.reservedStart || !this.reservedEnd || this.reservedEnd<=this.reservedStart)){this.error='Choose an end time after the reservation start.';return;}
    if(!this.reservationMode&&this.playerMobile.trim()){const n=this.playerMobile.replace(/[\s()-]/g,'');if(!this.playerName.trim()){this.error='Enter the customer name when recording a phone number.';return;}const valid=/^[6-9][0-9]{9}$/.test(n)||(/^\+[1-9][0-9]{7,14}$/.test(n)&&(!n.startsWith('+91')||/^\+91[6-9][0-9]{9}$/.test(n)));if(!valid){this.error='Enter a valid 10-digit mobile number starting with 6–9.';return;}}
    this.busy = true; this.error = '';
    const request = this.reservationMode ? this.service.reserve(this.startTarget.id, this.playerName.trim(),this.reservedStart,this.reservedEnd) : this.service.start(this.startTarget.id, this.playerName.trim(),this.plannedEndAt || undefined,this.playerMobile.trim()||undefined);
    request.subscribe({ next: () => { this.busy = false; this.startTarget = null; this.message = this.reservationMode ? 'Facility reserved.' : 'Session started.'; this.activeTab = this.reservationMode ? 'RESERVED' : 'RUNNING'; this.refresh(); }, error: failure => { this.busy = false; this.error = failure?.status===409 ? 'That facility is already booked or running during the selected time. Choose another time or facility.' : apiError(failure).description; } });
  }
  openStop(session:RunningSession):void {if(this.busy)return;this.stopTarget=session;this.stopQuote=null;this.discountAmount=0;this.discountReason='';this.quickStop=false;this.markUnsettled=false;this.paidAmount='';this.error='';this.refreshStopQuote();}
  refreshStopQuote():void {if(!this.stopTarget||this.quoteLoading||this.busy)return;const target=this.stopTarget;const generation=++this.quoteGeneration;this.quoteLoading=true;this.stopQuote=null;this.error='';this.service.quote(target.id).subscribe({next:quote=>{if(generation!==this.quoteGeneration){this.service.cancelQuote(target.id,quote.token).subscribe({error:()=>{}});return;}this.stopQuote=quote;this.quoteLoading=false;this.paidAmount=String(this.payable);},error:e=>{if(generation===this.quoteGeneration){this.quoteLoading=false;this.error=apiError(e).description;}}});}
  closeStop():void {if(this.busy)return;const target=this.stopTarget,quote=this.stopQuote;this.quoteGeneration++;this.quoteLoading=false;this.stopTarget=null;this.stopQuote=null;if(target&&quote)this.service.cancelQuote(target.id,quote.token).subscribe({error:()=>{}});}

  stop(): void {
    if (!this.stopTarget || !this.stopQuote || this.quoteLoading || this.busy) return;
    this.busy = true; this.error = '';
    this.service.stop(this.stopTarget.id, Number(this.paidAmount || 0), this.markUnsettled, this.quickStop,this.stopQuote.token,Number(this.discountAmount||0),this.discountReason.trim()).subscribe({ next: () => { this.busy = false; this.stopTarget = null; this.stopQuote=null; this.message = 'Session stopped.'; this.refresh(); }, error: failure => { this.busy = false; const error=apiError(failure);this.error=error.description;if(error.errorCode==='SESSION_QUOTE_STALE')this.stopQuote=null; } });
  }
  startReserved(session: RunningSession): void { this.action(this.service.startReserved(session.id), 'Reserved session started.'); }
  cancelReservation(session: RunningSession): void { this.action(this.service.cancelReservation(session.id), 'Reservation cancelled.'); }
  pause(session: RunningSession): void { this.action(this.service.pause(session.id), 'Session paused.'); }
  resume(session: RunningSession): void { this.action(this.service.resume(session.id), 'Session resumed.'); }
  settle(session: RunningSession): void {
    if(this.busy)return;
    this.settlementReturnFocus=document.activeElement as HTMLElement;
    this.settleTarget=session;this.settlementDiscount=0;this.settlementDiscountReason='';this.settlementAmount=Number(session.totalAmount || 0).toFixed(2);
    this.settlementError='';this.error='';
  }
  saveSettlement(): void {
    if(!this.settleTarget || this.busy)return;
    const raw=String(this.settlementAmount ?? '').trim();
    const amount=Number(raw);
    if(!raw || !Number.isFinite(amount) || amount<Number(this.settleTarget.paidAmount || 0) || amount>9999999999.99 || !/^\d+(\.\d{1,2})?$/.test(raw)) {
      this.settlementError='Enter a valid total with at most two decimals, at least the amount already paid.';return;
    }
    this.busy=true;this.settlementError='';
    this.service.settle(this.settleTarget.id,amount).subscribe({next:()=>{this.busy=false;this.closeSettlement();this.message='Session payment updated.';this.refresh();},error:failure=>{this.busy=false;this.settlementError=apiError(failure).description;}});
  }
  requestSettlementDiscount():void {if(!this.settleTarget||this.busy||!this.settlementDiscountReason.trim()||this.settlementDiscount<=0)return;this.busy=true;const id=this.settleTarget.id;this.service.requestDiscount(id,this.settlementDiscount,this.settlementDiscountReason.trim()).subscribe({next:()=>{this.busy=false;this.closeSettlement();this.message='Discount submitted; only approved discounts reduce the bill.';this.refresh();},error:e=>{this.busy=false;this.settlementError=apiError(e).description;}});}
  isSnooker(session:RunningSession):boolean{return (session.catalogueCode||session.sport).toUpperCase()==='SNOOKER';}
  amount(session: RunningSession): number {
    if (session.status !== 'STARTED') return Number(session.totalAmount || 0);
    const elapsed = Math.max(0, Math.floor((this.now - new Date(session.startedAt).getTime()) / 1000));
    const currentPause = session.pausedAt ? Math.max(0, Math.floor((this.now - new Date(session.pausedAt).getTime()) / 1000)) : 0;
    const seconds = Math.max(0, elapsed - session.totalPausedSeconds - currentPause);
    const numerator=BigInt(Math.round(Number(session.ratePerHour||0)*100))*BigInt(seconds)*1000000n;
    const scale8=(numerator+1800n)/3600n;const cents=(scale8+500000n)/1000000n;
    return this.isSnooker(session)?Number(cents/100n):Number(cents)/100;
  }
  duration(session: RunningSession): string {
    const elapsed = session.status === 'STARTED' ? Math.floor((this.now - new Date(session.startedAt).getTime()) / 1000) : session.durationSeconds;
    const currentPause = session.status === 'STARTED' && session.pausedAt ? Math.floor((this.now - new Date(session.pausedAt).getTime()) / 1000) : 0;
    let remaining = Math.max(0, session.status === 'STARTED' ? elapsed - session.totalPausedSeconds - currentPause : elapsed);
    const hours = Math.floor(remaining / 3600); remaining %= 3600;
    const minutes = Math.floor(remaining / 60); const seconds = remaining % 60;
    return [hours, minutes, seconds].map(value => String(value).padStart(2, '0')).join(':');
  }
  private action(request: import('rxjs').Observable<unknown>, success: string): void {
    if (this.busy) return;
    this.busy = true; this.error = '';
    request.subscribe({ next: () => { this.busy = false; this.message = success; this.refresh(); }, error: failure => { this.busy = false; this.error = apiError(failure).description; } });
  }
}