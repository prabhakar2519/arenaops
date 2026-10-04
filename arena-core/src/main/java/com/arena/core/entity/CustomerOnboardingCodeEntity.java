package com.arena.core.entity;

import com.arena.core.enums.InvitationStatus;
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
@Table(name = "CUSTOMER_ONBOARDING_CODE")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerOnboardingCodeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "customer_onboarding_code_seq")
  @SequenceGenerator(name = "customer_onboarding_code_seq", sequenceName = "seq_customer_onboarding_code", allocationSize = 1)
  @Column(name = "ID")
  private Long id;

  @Column(name = "CUSTOMER_ID", nullable = false)
  private Long customerId;

  @Column(name = "CODE", nullable = false, unique = true, length = 128)
  private String code;

  @Column(name = "STATUS", nullable = false, length = 30)
  private String status;

  @Column(name = "INVITED_EMAIL", length = 255)
  private String invitedEmail;

  @Column(name = "ACTIVATION_CODE_HASH", length = 128)
  private String activationCodeHash;

  @Enumerated(EnumType.STRING)
  @Column(name = "INVITATION_STATUS", length = 30)
  private InvitationStatus invitationStatus;

  @Column(name = "SENT_AT")
  private LocalDateTime sentAt;

  @Column(name = "CONSUMED_AT")
  private LocalDateTime consumedAt;

  @Column(name = "REVOKED_AT")
  private LocalDateTime revokedAt;

  @Column(name = "CREATED_BY_ADMIN_ID", length = 100)
  private String createdByAdminId;

  @Column(name = "REVOKED_BY_ADMIN_ID", length = 100)
  private String revokedByAdminId;

  @Column(name = "REVOCATION_REASON", length = 500)
  private String revocationReason;

  @Column(name = "MAX_USES", nullable = false)
  @Builder.Default
  private Integer maxUses = 1;

  @Column(name = "USED_COUNT", nullable = false)
  @Builder.Default
  private Integer usedCount = 0;

  @Column(name = "EXPIRES_AT", nullable = false)
  private LocalDateTime expiresAt;

  @Column(name = "USED_AT")
  private LocalDateTime usedAt;

  @Column(name = "USED_BY_USERNAME", length = 100)
  private String usedByUsername;

  @Column(name = "CREATED_AT")
  private LocalDateTime createdAt;

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
