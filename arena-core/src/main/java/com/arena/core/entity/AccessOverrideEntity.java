package com.arena.core.entity;

import com.arena.core.enums.AccessOverrideStatus;
import com.arena.core.enums.AccessOverrideType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ACCESS_OVERRIDE")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccessOverrideEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "access_override_seq")
  @SequenceGenerator(name = "access_override_seq", sequenceName = "seq_access_override", allocationSize = 1)
  @Column(name = "ID")
  private Long id;

  @Column(name = "CUSTOMER_ID", nullable = false)
  private Long customerId;

  @Column(name = "SUBSCRIPTION_ID")
  private Long subscriptionId;

  @Enumerated(EnumType.STRING)
  @Column(name = "OVERRIDE_TYPE", nullable = false, length = 40)
  private AccessOverrideType overrideType;

  @Enumerated(EnumType.STRING)
  @Column(name = "STATUS", nullable = false, length = 30)
  private AccessOverrideStatus status;

  @Column(name = "STARTS_AT", nullable = false)
  private LocalDateTime startsAt;

  @Column(name = "ENDS_AT", nullable = false)
  private LocalDateTime endsAt;

  @Column(name = "REASON", length = 500)
  private String reason;

  @Column(name = "CREATED_BY", length = 100)
  private String createdBy;

  @Column(name = "CREATED_AT")
  private LocalDateTime createdAt;

  @Column(name = "REVOKED_AT")
  private LocalDateTime revokedAt;

  @Column(name = "REVOKED_BY", length = 100)
  private String revokedBy;

  @Column(name = "REVOCATION_REASON", length = 500)
  private String revocationReason;

  @Column(name = "UPDATED_AT")
  private LocalDateTime updatedAt;

  @PrePersist
  void onCreate() {
    this.createdAt = LocalDateTime.now();
    this.updatedAt = LocalDateTime.now();
  }

  @PreUpdate
  void onUpdate() {
    this.updatedAt = LocalDateTime.now();
  }
}
