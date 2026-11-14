package com.arena.core.sessions;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
@Entity @Table(name="session_discount_request") @Getter @Setter
public class SessionDiscountRequest {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="academy_id",nullable=false) private Long academyId;
 @Column(name="session_id",nullable=false) private Long sessionId;
 @Column(name="amount",nullable=false,precision=12,scale=2) private java.math.BigDecimal amount;
 @Column(name="reason",nullable=false,length=500) private String reason;
 @Column(name="status",nullable=false,length=16) private String status;
 @Column(name="requested_by_id",nullable=false) private Long requestedById;
 @Column(name="requested_by",nullable=false,length=100) private String requestedBy;
 @Column(name="staff_id") private Long staffId;
 @Column(name="requested_at",nullable=false) private java.time.LocalDateTime requestedAt;
 @Column(name="decided_by_id") private Long decidedById;
 @Column(name="decided_by",length=100) private String decidedBy;
 @Column(name="decided_at") private java.time.LocalDateTime decidedAt;
 @Column(name="decision_reason",length=500) private String decisionReason;
 @Column(name="policy_snapshot",nullable=false,length=1000) private String policySnapshot;
}
