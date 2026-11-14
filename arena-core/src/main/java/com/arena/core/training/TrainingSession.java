package com.arena.core.training;

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
import java.time.LocalTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "training_session")
@Getter
@Setter
public class TrainingSession {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  @Column(name = "academy_id", nullable = false) private Long academyId;
  @Column(name = "batch_id", nullable = false) private Long batchId;
  @Column(name = "session_date", nullable = false) private LocalDate sessionDate;
  @Column(name = "start_time", nullable = false) private LocalTime startTime;
  @Column(name = "end_time", nullable = false) private LocalTime endTime;
  @Column(nullable = false, length = 16) private String status = "SCHEDULED";
  @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
  @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
  @Column(name = "created_by", length = 100) private String createdBy;
  @Column(name = "updated_by", length = 100) private String updatedBy;

  @PrePersist void onCreate() { createdAt = updatedAt = LocalDateTime.now(); }
  @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(); }
}