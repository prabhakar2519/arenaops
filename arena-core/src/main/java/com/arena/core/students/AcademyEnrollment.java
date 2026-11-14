package com.arena.core.students;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "academy_enrollment")
@Getter
@Setter
public class AcademyEnrollment {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(name = "academy_id", nullable = false) private Long academyId;
  @Column(name = "student_id", nullable = false) private Long studentId;
  @Column(name = "batch_id", nullable = false) private Long batchId;
  @Column(name = "start_date", nullable = false) private LocalDate startDate;
  @Column(name = "end_date") private LocalDate endDate;
  @Column(nullable = false, length = 16) private String status;
  @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
  @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
  @Column(name = "created_by", length = 100) private String createdBy;
  @Column(name = "updated_by", length = 100) private String updatedBy;

  @PrePersist void onCreate() { createdAt = updatedAt = LocalDateTime.now(); }
  @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(); }
}