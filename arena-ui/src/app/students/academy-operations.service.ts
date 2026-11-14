import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { StudentPage } from './students.service';

export interface BatchOption { id: number; name: string; sportId: number; status: string; }
export interface TrainingSession { id: number; batchId: number; batchName: string; sportId: number; date: string; startTime: string; endTime: string; status: string; }
export interface AttendanceRow { id: number; studentId: number; studentCode: string; studentName: string; sessionId: number; status: string; captureMethod: string; capturedAt: string; confirmedAt: string | null; note: string | null; }
export interface SessionStudent { studentId: number; studentCode: string; firstName: string; lastName: string; attendanceId: number | null; status: string; }
export interface Charge { id: number; studentId: number; studentCode: string; studentName: string; batchId: number | null; description: string; amount: number; collected: number; outstanding: number; dueDate: string; status: string; }
export interface CollectionSummary { charged: number; collected: number; outstanding: number; openCharges: number; overdueCharges: number; }

@Injectable({ providedIn: 'root' })
export class AcademyOperationsService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/academy';
  batches(): Observable<{ content: BatchOption[] }> { return this.http.get<{ content: BatchOption[] }>(`${this.base}/batches?size=100&status=ACTIVE`); }
  students(): Observable<StudentPage> { return this.http.get<StudentPage>(`${this.base}/students?size=100&status=ACTIVE`); }
  sessions(date: string): Observable<TrainingSession[]> { return this.http.get<TrainingSession[]>(`${this.base}/training-sessions`, { params: new HttpParams().set('date', date) }); }
  resolveSession(batchId: number, date: string): Observable<TrainingSession> { return this.http.post<TrainingSession>(`${this.base}/training-sessions`, { batchId, date }); }
  attendance(sessionId: number): Observable<AttendanceRow[]> { return this.http.get<AttendanceRow[]>(`${this.base}/training-sessions/${sessionId}/attendance`); }
  sessionStudents(sessionId: number): Observable<SessionStudent[]> { return this.http.get<SessionStudent[]>(`${this.base}/training-sessions/${sessionId}/students`); }
  capture(method: 'qr' | 'nfc', credential: string, sessionId: number): Observable<AttendanceRow> { return this.http.post<AttendanceRow>(`${this.base}/attendance/${method}`, { credential, sessionId }); }
  manual(studentId: number, sessionId: number, status: string): Observable<AttendanceRow> { return this.http.post<AttendanceRow>(`${this.base}/attendance/manual`, { studentId, sessionId, status }); }
  confirm(id: number, status: string, note: string): Observable<AttendanceRow> { return this.http.post<AttendanceRow>(`${this.base}/attendance/${id}/confirm`, { status, note }); }
  summary(): Observable<CollectionSummary> { return this.http.get<CollectionSummary>(`${this.base}/collections/summary`); }
  charges(): Observable<Charge[]> { return this.http.get<Charge[]>(`${this.base}/collections/charges`); }
  addCharge(body: unknown): Observable<Charge> { return this.http.post<Charge>(`${this.base}/collections/charges`, body); }
  addPayment(chargeId: number, body: unknown): Observable<unknown> { return this.http.post(`${this.base}/collections/charges/${chargeId}/payments`, body); }
}