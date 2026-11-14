package com.arena.core.sessions;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
@Entity @Table(name="session_stop_quote") @Getter @Setter
public class SessionStopQuote {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="token_hash",length=64,nullable=false,unique=true) private String tokenHash;
 @Column(name="academy_id",nullable=false) private Long academyId;
 @Column(name="session_id",nullable=false) private Long sessionId;
 @Column(name="session_version",nullable=false) private Long sessionVersion;
 @Column(name="actor_id",nullable=false) private Long actorId;
 @Column(name="captured_at",nullable=false) private java.time.LocalDateTime capturedAt;
 @Column(name="expires_at",nullable=false) private java.time.LocalDateTime expiresAt;
 @Column(name="consumed_at") private java.time.LocalDateTime consumedAt;
 @Column(name="billable_seconds",nullable=false) private Long billableSeconds;
 @Column(name="paused_seconds",nullable=false) private Long pausedSeconds;
 @Column(name="original_amount",nullable=false,precision=12,scale=2) private java.math.BigDecimal originalAmount;
}
