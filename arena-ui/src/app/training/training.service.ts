import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, EMPTY, expand, reduce } from 'rxjs';

export interface RecordBase { id: number; version: number; }
export interface CatalogueItem extends RecordBase { code: string; label: string; facilityKind: 'COURT' | 'TABLE' | null; }
export interface AcademySport extends RecordBase { name: string; catalogueCode: string | null; active: boolean; training: boolean; sessions: boolean; }
export interface AcademyStaff extends RecordBase { discountEnabled?:boolean;maxDiscountPercent?:number;maxDiscountAmount?:number;dailyDiscountAllowance?:number|null;discountApprovalAllowed?:boolean; appUserId: number | null; loginAccessStatus?: string; name: string; email: string | null; phone?: string; role: 'COACH' | 'STAFF'; designation?: string; managerAccess?:string; applicationRoles?: string[]; registrationRequestId?:string;loginState?:string; loginUsername?:string; notificationState?:string; active: boolean; sportIds: number[]; }
export interface TrainingBatch extends RecordBase { sportId: number; name: string; code: string; level?: string; capacity?: number | null; startDate: string; endDate?: string | null; status: 'DRAFT' | 'ACTIVE' | 'INACTIVE'; coachIds: number[]; leadCoachId?: number | null; }
export interface Weekly { dayOfWeek: number; startTime: string; endTime: string; effectiveFrom: string; effectiveTo?: string | null; }
export interface BatchDetail { batch: TrainingBatch; schedules: Weekly[]; }
export interface Page<T> { content: T[]; totalElements: number; totalPages: number; number: number; size: number; }
@Injectable({providedIn: 'root'})
export class TrainingService {
 private http = inject(HttpClient);
 private url = '/api/academy';
 list<T>(path: string, filters: Record<string,string|number|boolean> = {}): Observable<Page<T>> {
  let params = new HttpParams();
  for (const [key,value] of Object.entries(filters)) params=params.set(key,String(value));
  return this.http.get<Page<T>>(`${this.url}/${path}`, {params});
 }
 all<T>(path: string): Observable<T[]> {
  return this.list<T>(path,{size:100}).pipe(
   expand(p=>p.number+1<p.totalPages?this.list<T>(path,{page:p.number+1,size:100}):EMPTY),
   reduce((items:T[],p)=>items.concat(p.content),[]));
 }
 get<T>(path:string):Observable<T>{return this.http.get<T>(`${this.url}/${path}`);}
 create<T>(path: string, body: unknown): Observable<T> { return this.http.post<T>(`${this.url}/${path}`, body); }
 update<T>(path: string, body: unknown): Observable<T> { return this.http.put<T>(`${this.url}/${path}`, body); }
 detail(id: number): Observable<BatchDetail> { return this.http.get<BatchDetail>(`${this.url}/batches/${id}`); }
}
