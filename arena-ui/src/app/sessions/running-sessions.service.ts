import { inject, Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface RunningSession {
  playerMobile?:string|null;catalogueCode?:string;originalAmount?:number;approvedDiscount?:number;requestedDiscount?:number;discountStatus?:string;discountReason?:string;discountRequestId?:number;financialStatus?:string;financialVisible?:boolean;
  reservedStart?: string; reservedEnd?: string; plannedEndAt?: string;
  id: number; facilityId: number; facilityCode: string; facilityName: string; sportId: number; sport: string; facilityType: string;
  status: string; startedAt: string; endedAt: string | null; stoppedAt: string | null; pausedAt: string | null;
  durationSeconds: number; totalPausedSeconds: number; ratePerHour: number; currentAmount: number; totalAmount: number | null;
  paidAmount: number; outstandingAmount: number; paymentStatus: string; playerName: string | null; startedBy: string; stoppedBy: string | null; stopType: string | null;
}
export interface SessionFacility {
  id: number; sportId: number; sport: string; sportName: string; code: string; name: string; facilityType: string;
  active: boolean; hourlyRate: number | null; frameRate: number | null; configured: boolean; available: boolean;
}
export interface RunningDashboard {
  running: RunningSession[]; recent: RunningSession[]; reserved?: RunningSession[]; unsettled?: RunningSession[]; facilities: SessionFacility[];
  currentCharges: number; chargesToday: number; collectedToday: number; outstandingToday: number;
  facilitySummary: FacilitySummary[]; collectionsBySport: DailyCollection[];
}
export interface FacilitySummary {
  sportId: number; sport: string; sportName: string;
  activeFacilities: number; occupiedFacilities: number; availableFacilities: number;
}
export interface DailyCollection {
  sportId: number; sport: string; sportName: string;
  charged: number; collected: number; pending: number;
}

export interface DiscountAuthority {discountEnabled:boolean;canApprove:boolean;canViewFinancials:boolean;maxPercent:number|null;maxAmount:number|null;dailyRemaining:number|null;}
export interface StopQuote {token:string;sessionId:number;capturedAt:string;expiresAt:string;durationSeconds:number;totalPausedSeconds:number;originalBill:number;authority:DiscountAuthority;}
export interface DiscountRequest {id:number;sessionId:number;amount:number;reason:string;status:string;requestedBy:string;requestedAt:string;decidedBy?:string;decidedAt?:string;decisionReason?:string;policySnapshot?:string;}
export interface DiscountInbox {canApprove:boolean;requests:DiscountRequest[];}
export interface SessionCollection {sessionId:number;sportId:number;sport:string;amount:number;paymentDate:string;recordedAt:string;recordedBy:string;reference:string;}
export interface CustomerCheckin {id:number;facilityId:number;facility:string;name:string;mobile:string;mobileVerified:false;createdAt:string;expiresAt:string;}
export interface FacilityQr {token:string;facility:string;expiresAt:string|null;url:string;}
@Injectable({ providedIn: 'root' })
export class RunningSessionsService {
  private readonly http = inject(HttpClient);
  readonly permissions=signal<DiscountAuthority|null>(null);
  private readonly url = '/api/academy/running-sessions';
  checkins():Observable<CustomerCheckin[]>{return this.http.get<CustomerCheckin[]>('/api/academy/facility-check-ins');}
  issueCheckinQr(id:number):Observable<FacilityQr>{return this.http.post<FacilityQr>(`/api/academy/facilities/${id}/check-in-qr`,{});}
  confirmCheckin(id:number,plannedEndAt?:string):Observable<{sessionId:number;status:string}>{return this.http.post<{sessionId:number;status:string}>(`/api/academy/facility-check-ins/${id}/confirm`,{plannedEndAt});}
  cancelCheckin(id:number):Observable<void>{return this.http.post<void>(`/api/academy/facility-check-ins/${id}/cancel`,{});}
  dashboard(): Observable<RunningDashboard> { return this.http.get<RunningDashboard>(`${this.url}/dashboard`); }
  history(params: Record<string,string|number>): Observable<{content:RunningSession[];totalElements:number;totalPages:number}> { return this.http.get<{content:RunningSession[];totalElements:number;totalPages:number}>(`${this.url}/history`,{params}); }
  exportHistory(params:Record<string,string>):Observable<Blob>{return this.http.get(`${this.url}/history/export`,{params,responseType:'blob'});}
  start(facilityId: number, playerName: string, plannedEndAt?: string, mobile?: string): Observable<RunningSession> { return this.http.post<RunningSession>(this.url, { facilityId, playerName: playerName || null, plannedEndAt, ...(mobile ? {mobile} : {}) }); }
  reserve(facilityId: number, playerName: string, reservedStart: string, reservedEnd: string): Observable<RunningSession> { return this.http.post<RunningSession>(`${this.url}/reserve`, { facilityId, playerName: playerName || null, reservedStart, reservedEnd }); }
  startReserved(id: number): Observable<RunningSession> { return this.http.post<RunningSession>(`${this.url}/${id}/start-reserved`, {}); }
  cancelReservation(id: number): Observable<RunningSession> { return this.http.post<RunningSession>(`${this.url}/${id}/cancel-reservation`, {}); }
  stop(id: number, paidAmount: number, markUnsettled: boolean, quickStop: boolean,quoteToken:string,discountAmount=0,discountReason=''): Observable<RunningSession> { return this.http.post<RunningSession>(`${this.url}/${id}/stop`, { paidAmount, markUnsettled, quickStop,quoteToken,discountAmount,discountReason }); }
  pause(id: number): Observable<RunningSession> { return this.http.post<RunningSession>(`${this.url}/${id}/pause`, {}); }
  resume(id: number): Observable<RunningSession> { return this.http.post<RunningSession>(`${this.url}/${id}/resume`, {}); }
  settle(id: number, paidAmount: number): Observable<RunningSession> { return this.http.post<RunningSession>(`${this.url}/${id}/settle`, null, { params: { paidAmount } }); }
  quote(id:number):Observable<StopQuote>{return this.http.post<StopQuote>(`${this.url}/${id}/stop-quote`,{});}
  cancelQuote(id:number,token:string):Observable<void>{return this.http.post<void>(`${this.url}/${id}/stop-quote/cancel`,{token});}
  authority():Observable<DiscountAuthority>{return this.http.get<DiscountAuthority>(`${this.url}/authority`);}
  inbox():Observable<DiscountInbox>{return this.http.get<DiscountInbox>(`${this.url}/discount-requests`);}
  decision(id:number,approve:boolean,reason:string):Observable<DiscountRequest>{return this.http.post<DiscountRequest>(`${this.url}/discount-requests/${id}/decision`,{approve,reason});}
  requestDiscount(id:number,amount:number,reason:string):Observable<void>{return this.http.post<void>(`${this.url}/${id}/discount-requests`,{amount,reason});}
  discountRequest(id:number):Observable<DiscountRequest>{return this.http.get<DiscountRequest>(`${this.url}/discount-requests/${id}`);}
  cancelDiscount(id:number):Observable<DiscountRequest>{return this.http.post<DiscountRequest>(`${this.url}/discount-requests/${id}/cancel`,{});}
  collections(from:string,to:string):Observable<SessionCollection[]>{return this.http.get<SessionCollection[]>(`${this.url}/collections`,{params:{from,to}});}
}