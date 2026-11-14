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

@Entity @Table(name = "academy_payment") @Getter @Setter
public class AcademyPayment {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  @Column(name = "academy_id", nullable = false) private Long academyId;
  @Column(name = "student_id") private Long studentId;
  @Column(name="session_id") private Long sessionId;
  @Column(nullable = false, precision = 12, scale = 2) private BigDecimal amount;
  @Column(name = "payment_date", nullable = false) private LocalDate paymentDate;
  @Column(nullable = false, length = 16) private String method;
  @Column(length = 120) private String reference;
  @Column(length = 500) private String notes;
  @Column(name = "recorded_at", nullable = false) private LocalDateTime recordedAt;
  @Column(name = "recorded_by", length = 100) private String recordedBy;
  @PrePersist void onCreate() { if (recordedAt == null) recordedAt = LocalDateTime.now(); }
}