import {UiFeedbackService} from '../ui/ui-feedback.service';
import {tap} from 'rxjs';
import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { apiError } from '../api-error';
import { BarcodeFormat, QRCodeWriter } from '@zxing/library';
import { BatchOption, Enrollment, RegisteredSport, Student, StudentRequest, StudentsService } from './students.service';

@Component({
  selector: 'app-students',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './students.component.html',
  styleUrl: './students.component.scss'
})
export class StudentsComponent implements OnInit {
  readonly feedback=inject(UiFeedbackService);
  private readonly service = inject(StudentsService);
  students: Student[] = [];
  sports: RegisteredSport[] = [];
  batches: BatchOption[] = [];
  enrollments: Enrollment[] = [];
  selected: Student | null = null;
  editing: Student | null = null;
  formOpen = false;
  search = '';
  status = '';
  page = 0;
  totalPages = 0;
  loading = true;
  saving = false;
  error = '';
  message = '';
  form = this.emptyForm();
  selectedSports = new Set<number>();
  enrollmentBatchId = 0;
  enrollmentStartDate = new Date().toISOString().slice(0, 10);
  qrImage = '';
  qrBusy = false;

  ngOnInit(): void {
    this.service.sports().subscribe({
      next: result => this.sports = result.content.filter(sport => sport.active),
      error: failure => this.error = apiError(failure).description
    });
    this.load();
  }

  load(): void {
    this.loading = true;
    this.error = '';
    this.service.list(this.search.trim(), this.status, this.page).subscribe({
      next: result => { this.students = result.content; this.totalPages = result.totalPages; this.loading = false; },
      error: failure => { this.error = apiError(failure).description; this.loading = false; }
    });
  }

  filter(): void { this.page = 0; this.load(); }
  paginate(delta: number): void { this.page += delta; this.load(); }
  sportName(id: number): string { return this.sports.find(sport => sport.id === id)?.name || 'Activity'; }
  sportNames(ids: number[]): string { return ids.map(id => this.sportName(id)).join(', '); }

  newStudent(): void {
    this.editing = null;
    this.selected = null;
    this.formOpen = true;
    this.form = this.emptyForm();
    this.selectedSports.clear();
    this.error = '';
    this.message = '';
  }

  editStudent(student: Student): void {
    this.editing = student;
    this.selected = null;
    this.formOpen = true;
    this.form = {
      firstName: student.firstName, lastName: student.lastName, dateOfBirth: student.dateOfBirth || '', gender: student.gender || '',
      email: student.email || '', phone: student.phone || '', guardianName: student.guardianName || '',
      guardianPhone: student.guardianPhone || '', guardianEmail: student.guardianEmail || '', address: student.address || '',
      emergencyContact: student.emergencyContact || '', notes: student.notes || '', status: student.status
    };
    this.selectedSports = new Set(student.sportIds);
    this.error = '';
    this.message = '';
  }

  viewStudent(student: Student): void { this.selected = student; this.editing = null; }
  closeForm(): void { if (!this.saving) { this.editing = null; this.formOpen = false; } }

  toggleSport(id: number): void {
    if (this.selectedSports.has(id)) this.selectedSports.delete(id);
    else this.selectedSports.add(id);
  }

  loadEnrollments(student: Student): void {
    this.selected = student;
    this.service.batches().subscribe({ next: result => this.batches = result.content, error: failure => this.error = apiError(failure).description });
    this.service.getEnrollments(student.id).subscribe({ next: result => this.enrollments = result, error: failure => this.error = apiError(failure).description });
  }

  enrollSelected(): void {
    if (!this.selected || !this.enrollmentBatchId) return;
    this.service.enroll(this.selected.id, this.enrollmentBatchId, this.enrollmentStartDate).subscribe({
      next: () => { this.message = 'Student enrolled.';this.feedback.success(this.message); this.enrollmentBatchId = 0; this.loadEnrollments(this.selected!); },
      error: failure => this.error = apiError(failure).description
    });
  }

  endEnrollment(enrollment: Enrollment): void {
    this.feedback.confirm({id:`end-enrollment-${enrollment.id}`,title:'End enrollment?',message:`End ${enrollment.studentName}'s enrollment in ${enrollment.batchName}?`,confirmText:'End Enrollment',variant:'destructive',successMessage:'Enrollment ended.',action:()=>this.service.endEnrollment(enrollment.id).pipe(tap({
      next:()=>{this.message='Enrollment ended.';if(this.selected)this.loadEnrollments(this.selected);},
      error:failure=>this.error=apiError(failure).description
    }))}).subscribe();
  }

  get availableBatches(): BatchOption[] {
    const activeBatchIds = new Set(this.enrollments.filter(enrollment => enrollment.status === 'ACTIVE').map(enrollment => enrollment.batchId));
    const selectedSportIds = new Set(this.selected?.sportIds || []);
    return this.batches.filter(batch => selectedSportIds.has(batch.sportId) && !activeBatchIds.has(batch.id));
  }

  issueQr(): void {
    if (!this.selected || this.qrBusy) return;
    this.qrBusy = true; this.error = ''; this.qrImage = '';
    this.service.issueQr(this.selected.id).subscribe({
      next: credential => {
        try {
          const matrix = new QRCodeWriter().encode(credential.value, BarcodeFormat.QR_CODE, 280, 280, new Map());
          const canvas = document.createElement('canvas');
          canvas.width = matrix.getWidth(); canvas.height = matrix.getHeight();
          const context = canvas.getContext('2d');
          if (!context) throw new Error('Canvas is unavailable');
          const image = context.createImageData(canvas.width, canvas.height);
          for (let y = 0; y < matrix.getHeight(); y++) for (let x = 0; x < matrix.getWidth(); x++) {
            const offset = (y * canvas.width + x) * 4;
            const color = matrix.get(x, y) ? 0 : 255;
            image.data[offset] = color; image.data[offset + 1] = color; image.data[offset + 2] = color; image.data[offset + 3] = 255;
          }
          context.putImageData(image, 0, 0); this.qrImage = canvas.toDataURL('image/png'); this.qrBusy = false;
        } catch { this.error = 'QR rendering failed. Replace the credential and try again.'; this.qrBusy = false; }
      },
      error: failure => { this.error = apiError(failure).description; this.qrBusy = false; }
    });
  }

  save(): void {
    if (this.saving || !this.form.firstName.trim() || !this.form.lastName.trim()) return;
    this.saving = true;
    this.error = '';
    const request: StudentRequest = {
      ...this.form,
      firstName: this.form.firstName.trim(),
      lastName: this.form.lastName.trim(),
      dateOfBirth: this.form.dateOfBirth || null,
      gender: this.form.gender || null,
      email: this.form.email || null,
      phone: this.form.phone || null,
      guardianName: this.form.guardianName || null,
      guardianPhone: this.form.guardianPhone || null,
      guardianEmail: this.form.guardianEmail || null,
      address: this.form.address || null,
      emergencyContact: this.form.emergencyContact || null,
      notes: this.form.notes || null,
      sportIds: [...this.selectedSports]
    };
    if (this.editing) request.version = this.editing.version;
    const request$ = this.editing ? this.service.update(this.editing.id, request) : this.service.create(request);
    request$.subscribe({
      next: () => { this.saving = false; this.editing = null; this.formOpen = false; this.message = 'Student saved.';this.feedback.success(this.message); this.load(); },
      error: failure => { this.saving = false; this.error = apiError(failure).description; }
    });
  }

  toggleStatus(student: Student): void {
    const next = student.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
    this.feedback.confirm({id:`student-status-${student.id}`,title:`${next==='INACTIVE'?'Deactivate':'Activate'} student?`,message:`${next==='INACTIVE'?'Deactivate':'Activate'} ${student.firstName} ${student.lastName}?`,confirmText:next==='INACTIVE'?'Deactivate':'Activate',variant:next==='INACTIVE'?'destructive':'primary',successMessage:`Student ${next.toLowerCase()}.`,action:()=>{
    return this.service.update(student.id, {
      firstName: student.firstName, lastName: student.lastName, dateOfBirth: student.dateOfBirth, gender: student.gender,
      email: student.email, phone: student.phone, guardianName: student.guardianName, guardianPhone: student.guardianPhone,
      guardianEmail: student.guardianEmail, address: student.address, emergencyContact: student.emergencyContact,
      notes: student.notes, sportIds: student.sportIds, status: next, version: student.version
    }).pipe(tap({
      next: () => { this.message = `Student ${next.toLowerCase()}.`; this.load(); },
      error: failure => this.error = apiError(failure).description
    }));
    }}).subscribe();
  }

  private emptyForm() {
    return { firstName: '', lastName: '', dateOfBirth: '', gender: '', email: '', phone: '', guardianName: '', guardianPhone: '', guardianEmail: '', address: '', emergencyContact: '', notes: '', status: 'ACTIVE' as 'ACTIVE' | 'INACTIVE' };
  }
}