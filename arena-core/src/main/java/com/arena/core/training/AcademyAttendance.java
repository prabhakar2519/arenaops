package com.arena.core.training;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "academy_attendance") @Getter @Setter
public class AcademyAttendance {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  @Column(name = "academy_id", nullable = false) private Long academyId;
  @Column(name = "session_id", nullable = false) private Long sessionId;
  @Column(name = "enrollment_id", nullable = false) private Long enrollmentId;
  @Column(name = "student_id", nullable = false) private Long studentId;
  @Column(nullable = false, length = 16) private String status;
  @Column(name = "capture_method", nullable = false, length = 8) private String captureMethod;
  @Column(name = "captured_at", nullable = false) private LocalDateTime capturedAt;
  @Column(name = "confirmed_at") private LocalDateTime confirmedAt;
  @Column(name = "captured_by", length = 100) private String capturedBy;
  @Column(name = "confirmed_by", length = 100) private String confirmedBy;
  @Column(length = 500) private String note;
  @PrePersist void onCreate() { capturedAt = LocalDateTime.now(); }
}