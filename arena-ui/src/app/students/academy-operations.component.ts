import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { ElementRef, ViewChild } from '@angular/core';
import { BrowserQRCodeReader, IScannerControls } from '@zxing/browser';
import { apiError } from '../api-error';
import { AcademyOperationsService, AttendanceRow, BatchOption, Charge, CollectionSummary, SessionStudent, TrainingSession } from './academy-operations.service';
import { Student } from './students.service';

@Component({ selector: 'app-academy-operations', standalone: true, imports: [CommonModule, FormsModule], templateUrl: './academy-operations.component.html', styleUrl: './academy-operations.component.scss' })
export class AcademyOperationsComponent implements OnInit {
  private readonly service = inject(AcademyOperationsService);
  private readonly route = inject(ActivatedRoute);
  @ViewChild('cameraVideo') cameraVideo?: ElementRef<HTMLVideoElement>;
  private readonly qrReader = new BrowserQRCodeReader();
  private scannerControls?: IScannerControls;
  section: 'attendance' | 'collections' = 'attendance';
  loading = true; busy = false; error = ''; message = '';
  today = new Date().toISOString().slice(0, 10);
  date = this.today;
  batches: BatchOption[] = []; students: Student[] = []; sessions: TrainingSession[] = []; attendance: AttendanceRow[] = [];
  roster: SessionStudent[] = [];
  selectedSessionId = 0; batchId = 0; studentId = 0; credential = ''; captureMethod: 'qr' | 'nfc' = 'qr'; manualStatus = 'PRESENT';
  summary: CollectionSummary | null = null; charges: Charge[] = []; chargeForm = { studentId: 0, batchId: null as number | null, description: 'Training fee', amount: '', dueDate: this.today };
  paymentCharge: Charge | null = null; paymentForm = { amount: '', paymentDate: this.today, method: 'CASH', reference: '', notes: '' };
  cameraActive = false;

  ngOnInit(): void {
    this.route.data.subscribe(data => { this.section = data['section'] === 'collections' ? 'collections' : 'attendance'; this.refresh(); });
  }

  get title(): string { return this.section === 'attendance' ? 'Attendance' : 'Student Collections'; }
  get provisional(): AttendanceRow[] { return this.attendance.filter(row => row.status === 'PROVISIONAL'); }

  async startCamera(): Promise<void> {
    if (!window.isSecureContext || !navigator.mediaDevices?.getUserMedia) {
      this.error = 'Camera scanning requires HTTPS or localhost and a browser with camera access.';
      return;
    }
    this.error = ''; this.cameraActive = true;
    await new Promise(resolve => setTimeout(resolve, 0));
    if (!this.cameraVideo?.nativeElement) { this.cameraActive = false; this.error = 'Camera preview could not be opened.'; return; }
    try {
      this.scannerControls = await this.qrReader.decodeFromVideoDevice(undefined, this.cameraVideo.nativeElement, (result) => {
        if (!result || this.busy) return;
        this.credential = result.getText(); this.stopCamera(); this.capture();
      });
    } catch {
      this.cameraActive = false;
      this.error = 'Camera access was denied or no usable camera was found.';
    }
  }

  stopCamera(): void { this.scannerControls?.stop(); this.scannerControls = undefined; this.cameraActive = false; }

  refresh(): void {
    this.loading = true; this.error = ''; this.message = '';
    if (this.section === 'attendance') {
      this.service.batches().subscribe({ next: value => this.batches = value.content, error: failure => this.error = apiError(failure).description });
      this.service.students().subscribe({ next: value => this.students = value.content, error: failure => this.error = apiError(failure).description });
      this.loadSessions();
    } else {
      this.service.students().subscribe({ next: value => this.students = value.content, error: failure => this.error = apiError(failure).description });
      this.service.batches().subscribe({ next: value => this.batches = value.content, error: failure => this.error = apiError(failure).description });
      this.service.summary().subscribe({ next: value => this.summary = value, error: failure => this.error = apiError(failure).description });
      this.service.charges().subscribe({ next: value => { this.charges = value; this.loading = false; }, error: failure => { this.error = apiError(failure).description; this.loading = false; } });
    }
  }

  loadSessions(): void {
    this.loading = true;
    this.service.sessions(this.date).subscribe({ next: value => { this.sessions = value; this.loading = false; if (!value.some(session => session.id === this.selectedSessionId)) this.selectedSessionId = value[0]?.id || 0; this.loadAttendance(); }, error: failure => { this.error = apiError(failure).description; this.loading = false; } });
  }

  resolveSession(): void {
    if (!this.batchId || this.busy) return;
    this.busy = true; this.error = '';
    this.service.resolveSession(this.batchId, this.date).subscribe({ next: value => { this.busy = false; this.message = 'Training session ready.'; this.loadSessions(); this.selectedSessionId = value.id; }, error: failure => { this.busy = false; this.error = apiError(failure).description; } });
  }

  loadAttendance(): void {
    if (!this.selectedSessionId) { this.attendance = []; this.roster = []; return; }
    this.service.attendance(this.selectedSessionId).subscribe({ next: value => this.attendance = value, error: failure => this.error = apiError(failure).description });
    this.service.sessionStudents(this.selectedSessionId).subscribe({ next: value => this.roster = value, error: failure => this.error = apiError(failure).description });
  }

  capture(): void {
    if (!this.credential.trim() || !this.selectedSessionId || this.busy) return;
    this.busy = true; this.error = ''; this.message = '';
    this.service.capture(this.captureMethod, this.credential.trim(), this.selectedSessionId).subscribe({ next: value => { this.busy = false; this.credential = ''; this.message = value.status === 'PROVISIONAL' ? `${value.studentName} checked in. Coach confirmation is pending.` : 'Attendance already exists.'; this.loadAttendance(); }, error: failure => { this.busy = false; this.error = apiError(failure).description; } });
  }

  manual(): void {
    if (!this.studentId || !this.selectedSessionId || this.busy) return;
    this.busy = true; this.error = '';
    this.service.manual(this.studentId, this.selectedSessionId, this.manualStatus).subscribe({ next: () => { this.busy = false; this.message = 'Attendance saved.'; this.loadAttendance(); }, error: failure => { this.busy = false; this.error = apiError(failure).description; } });
  }

  confirm(row: AttendanceRow, status: string): void {
    this.service.confirm(row.id, status, '').subscribe({ next: () => { this.message = 'Attendance confirmed.'; this.loadAttendance(); }, error: failure => this.error = apiError(failure).description });
  }

  createCharge(): void {
    if (!this.chargeForm.studentId || !this.chargeForm.description.trim() || !this.chargeForm.amount || this.busy) return;
    this.busy = true; this.error = '';
    this.service.addCharge({ ...this.chargeForm, amount: Number(this.chargeForm.amount), batchId: this.chargeForm.batchId || null }).subscribe({ next: () => { this.busy = false; this.message = 'Fee charge recorded.'; this.refresh(); }, error: failure => { this.busy = false; this.error = apiError(failure).description; } });
  }

  openPayment(charge: Charge): void { this.paymentCharge = charge; this.paymentForm = { amount: '', paymentDate: this.today, method: 'CASH', reference: '', notes: '' }; }
  recordPayment(): void {
    if (!this.paymentCharge || !this.paymentForm.amount || this.busy) return;
    this.busy = true; this.error = '';
    this.service.addPayment(this.paymentCharge.id, { ...this.paymentForm, amount: Number(this.paymentForm.amount) }).subscribe({ next: () => { this.busy = false; this.paymentCharge = null; this.message = 'Payment recorded.'; this.refresh(); }, error: failure => { this.busy = false; this.error = apiError(failure).description; } });
  }

  ngOnDestroy(): void { this.stopCamera(); }
}