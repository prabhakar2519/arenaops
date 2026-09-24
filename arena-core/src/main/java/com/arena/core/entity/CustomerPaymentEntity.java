package com.arena.core.entity;

import com.arena.core.enums.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "CUSTOMER_PAYMENT")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerPaymentEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "customer_payment_seq")
  @SequenceGenerator(name = "customer_payment_seq", sequenceName = "seq_customer_payment", allocationSize = 1)
  @Column(name = "ID")
  private Long id;

  @Column(name = "CUSTOMER_ID", nullable = false)
  private Long customerId;

  @Column(name = "SUBSCRIPTION_ID")
  private Long subscriptionId;

  @Column(name = "AMOUNT", nullable = false, precision = 12, scale = 2)
  private BigDecimal amount;

  @Column(name = "CURRENCY", nullable = false, length = 10)
  private String currency;

  @Enumerated(EnumType.STRING)
  @Column(name = "PAYMENT_STATUS", nullable = false, length = 30)
  private PaymentStatus paymentStatus;

  @Column(name = "PAYMENT_METHOD", length = 80)
  private String paymentMethod;

  @Column(name = "PAYMENT_REFERENCE", length = 160)
  private String paymentReference;

  @Column(name = "PAYMENT_DATE")
  private LocalDateTime paymentDate;

  @Column(name = "RECORDED_BY", length = 100)
  private String recordedBy;

  @Column(name = "NOTES", length = 1000)
  private String notes;

  @Column(name = "CREATED_AT")
  private LocalDateTime createdAt;

  @PrePersist
  void onCreate() {
    this.createdAt = LocalDateTime.now();
  }
}
