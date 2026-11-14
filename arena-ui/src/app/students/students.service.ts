import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface Student {
  id: number;
  studentCode: string;
  firstName: string;
  lastName: string;
  dateOfBirth: string | null;
  gender: string | null;
  email: string | null;
  phone: string | null;
  guardianName: string | null;
  guardianPhone: string | null;
  guardianEmail: string | null;
  address: string | null;
  emergencyContact: string | null;
  notes: string | null;
  status: 'ACTIVE' | 'INACTIVE';
  sportIds: number[];
  createdAt: string;
  version: number;
}

export interface StudentRequest {
  firstName: string;
  lastName: string;
  dateOfBirth: string | null;
  gender: string | null;
  email: string | null;
  phone: string | null;
  guardianName: string | null;
  guardianPhone: string | null;
  guardianEmail: string | null;
  address: string | null;
  emergencyContact: string | null;
  notes: string | null;
  sportIds: number[];
  status: 'ACTIVE' | 'INACTIVE';
  version?: number;
}

export interface StudentPage {
  content: Student[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface RegisteredSport { id: number; name: string; active: boolean; training: boolean; }
export interface Enrollment { id: number; studentId: number; studentCode: string; studentName: string; batchId: number; batchName: string; startDate: string; endDate: string | null; status: 'ACTIVE' | 'ENDED'; }
export interface BatchOption { id: number; name: string; sportId: number; status: string; }
export interface IssuedCredential { id: number; kind: string; value: string; message: string; }

@Injectable({ providedIn: 'root' })
export class StudentsService {
  private readonly http = inject(HttpClient);
  private readonly url = '/api/academy/students';

  list(search = '', status = '', page = 0): Observable<StudentPage> {
    let params = new HttpParams().set('search', search).set('page', page).set('size', 20);
    if (status) params = params.set('status', status);
    return this.http.get<StudentPage>(this.url, { params });
  }

  get(id: number): Observable<Student> { return this.http.get<Student>(`${this.url}/${id}`); }
  create(request: StudentRequest): Observable<Student> { return this.http.post<Student>(this.url, request); }
  update(id: number, request: StudentRequest): Observable<Student> { return this.http.put<Student>(`${this.url}/${id}`, request); }
  sports(): Observable<{ content: RegisteredSport[] }> { return this.http.get<{ content: RegisteredSport[] }>('/api/academy/sports?size=100'); }
  batches(): Observable<{ content: BatchOption[] }> { return this.http.get<{ content: BatchOption[] }>('/api/academy/batches?size=100&status=ACTIVE'); }
  enroll(studentId: number, batchId: number, startDate: string): Observable<Enrollment> { return this.http.post<Enrollment>('/api/academy/enrollments', { studentId, batchId, startDate }); }
  endEnrollment(id: number): Observable<Enrollment> { return this.http.post<Enrollment>(`/api/academy/enrollments/${id}/end`, {}); }
  getEnrollments(studentId: number): Observable<Enrollment[]> { return this.http.get<Enrollment[]>(`/api/academy/students/${studentId}/enrollments`); }
  issueQr(studentId: number): Observable<IssuedCredential> { return this.http.post<IssuedCredential>(`/api/academy/students/${studentId}/attendance/qr`, {}); }
}