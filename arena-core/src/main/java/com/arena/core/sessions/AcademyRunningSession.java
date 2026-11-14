package com.arena.core.sessions;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "academy_running_session") @Getter @Setter
public class AcademyRunningSession {
  @jakarta.persistence.Version private Long version=0L;
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  @Column(name = "academy_id", nullable = false) private Long academyId;
  @Column(name = "sport_id", nullable = false) private Long sportId;
  @Column(name = "facility_id", nullable = false) private Long facilityId;
  @Column(nullable = false, length = 16) private String status;
  @Column(name = "started_at", nullable = false) private LocalDateTime startedAt;
  @Column(name = "reserved_start") private LocalDateTime reservedStart;
  @Column(name = "reserved_end") private LocalDateTime reservedEnd;
  @Column(name = "planned_end_at") private LocalDateTime plannedEndAt;
  @Column(name = "ended_at") private LocalDateTime endedAt;
  @Column(name = "stopped_at") private LocalDateTime stoppedAt;
  @Column(name = "paused_at") private LocalDateTime pausedAt;
  @Column(name = "total_paused_seconds", nullable = false) private Long totalPausedSeconds = 0L;
  @Column(name = "rate_per_hour", nullable = false, precision = 12, scale = 2) private BigDecimal ratePerHour;
  @Column(name="original_amount",precision=12,scale=2) private BigDecimal originalAmount;
  @Column(name="approved_discount",nullable=false,precision=12,scale=2) private BigDecimal approvedDiscount=BigDecimal.ZERO;
  @Column(name="requested_discount",nullable=false,precision=12,scale=2) private BigDecimal requestedDiscount=BigDecimal.ZERO;
  @Column(name="discount_status",nullable=false,length=16) private String discountStatus="NONE";
  @Column(name="discount_reason",length=500) private String discountReason;
  @Column(name="discount_request_id") private Long discountRequestId;
  @Column(name = "total_amount", precision = 12, scale = 2) private BigDecimal totalAmount;
  @Column(name = "paid_amount", nullable = false, precision = 12, scale = 2) private BigDecimal paidAmount = BigDecimal.ZERO;
  @Column(name = "player_name", length = 160) private String playerName;
  @Column(length = 500) private String notes;
  @Column(name = "stop_type", length = 20) private String stopType;
  @Column(name = "started_by", nullable = false, length = 100) private String startedBy;
  @Column(name = "stopped_by", length = 100) private String stoppedBy;
  @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
  @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;

  @PrePersist void onCreate() { createdAt = updatedAt = LocalDateTime.now(); }
  @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(); }
}