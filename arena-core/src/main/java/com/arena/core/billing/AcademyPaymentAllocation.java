package com.arena.core.billing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.PrePersist;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "academy_payment_allocation") @Getter @Setter
public class AcademyPaymentAllocation {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  @Column(name = "academy_id", nullable = false) private Long academyId;
  @Column(name = "payment_id", nullable = false) private Long paymentId;
  @Column(name = "charge_id", nullable = false) private Long chargeId;
  @Column(nullable = false, precision = 12, scale = 2) private BigDecimal amount;
  @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
  @PrePersist void onCreate() { if (createdAt == null) createdAt = LocalDateTime.now(); }
}