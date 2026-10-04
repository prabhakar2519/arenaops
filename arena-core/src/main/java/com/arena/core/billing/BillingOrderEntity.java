package com.arena.core.billing;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import static com.arena.core.billing.BillingTypes.*;

/** Immutable price snapshot and durable checkout state scoped to a customer. */
@Entity @Table(name="BILLING_ORDER") @Getter @Setter @NoArgsConstructor
public class BillingOrderEntity {
  @Id private UUID id;
  @Column(nullable=false) private Long customerId;
  @Column(nullable=false) private Long subscriptionId;
  @Column(nullable=false) private UUID requestId;
  @Column(nullable=false) private String planId;
  @Column(nullable=false) private String planName;
  @Enumerated(EnumType.STRING) @Column(nullable=false) private Cycle cycle;
  @Column(nullable=false,precision=12,scale=2) private BigDecimal baseAmount;
  @Column(nullable=false,precision=12,scale=2) private BigDecimal taxAmount;
  @Column(nullable=false,precision=12,scale=2) private BigDecimal total;
  @Column(nullable=false) private String currency;
  @Enumerated(EnumType.STRING) @Column(nullable=false) private OrderStatus status;
  @Column(nullable=false) private LocalDateTime createdAt;
  @Column(nullable=false) private LocalDateTime updatedAt;
  @Column(nullable=false) private LocalDateTime expiresAt;
}
