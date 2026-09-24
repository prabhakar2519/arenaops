package com.arena.core.entity;

import com.arena.core.enums.AuditActorType;
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
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "AUDIT_EVENT")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditEventEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "audit_event_seq")
  @SequenceGenerator(name = "audit_event_seq", sequenceName = "seq_audit_event", allocationSize = 1)
  @Column(name = "ID")
  private Long id;

  @Column(name = "EVENT_TYPE", nullable = false, length = 80)
  private String eventType;

  @Column(name = "CUSTOMER_ID")
  private Long customerId;

  @Enumerated(EnumType.STRING)
  @Column(name = "ACTOR_TYPE", nullable = false, length = 30)
  private AuditActorType actorType;

  @Column(name = "ACTOR_ID", length = 120)
  private String actorId;

  @Column(name = "OCCURRED_AT", nullable = false)
  private LocalDateTime occurredAt;

  @Column(name = "PREVIOUS_STATE", length = 1200)
  private String previousState;

  @Column(name = "NEW_STATE", length = 1200)
  private String newState;

  @Column(name = "REASON", length = 500)
  private String reason;

  @Column(name = "METADATA", length = 2000)
  private String metadata;

  @PrePersist
  void onCreate() {
    if (this.occurredAt == null) {
      this.occurredAt = LocalDateTime.now();
    }
  }
}
