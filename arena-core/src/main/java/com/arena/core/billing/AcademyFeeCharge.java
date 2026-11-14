package com.arena.core.billing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.PrePersist;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "academy_fee_charge") @Getter @Setter
public class AcademyFeeCharge {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  @Column(name = "academy_id", nullable = false) private Long academyId;
  @Column(name = "student_id", nullable = false) private Long studentId;
  @Column(name = "batch_id") private Long batchId;
  @Column(nullable = false, length = 160) private String description;
  @Column(nullable = false, precision = 12, scale = 2) private BigDecimal amount;
  @Column(name = "due_date", nullable = false) private LocalDate dueDate;
  @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
  @Column(name = "created_by", length = 100) private String createdBy;
  @PrePersist void onCreate() { if (createdAt == null) createdAt = LocalDateTime.now(); }
}