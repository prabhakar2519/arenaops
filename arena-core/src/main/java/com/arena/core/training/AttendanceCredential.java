package com.arena.core.training;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "academy_attendance_credential") @Getter @Setter
public class AttendanceCredential {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  @Column(name = "academy_id", nullable = false) private Long academyId;
  @Column(name = "student_id", nullable = false) private Long studentId;
  @Column(nullable = false, length = 8) private String kind;
  @Column(name = "token_hash", nullable = false, unique = true, length = 64) private String tokenHash;
  @Column(nullable = false) private boolean active = true;
  @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
  @Column(name = "revoked_at") private LocalDateTime revokedAt;
  @Column(name = "created_by", length = 100) private String createdBy;
  @PrePersist void onCreate() { createdAt = LocalDateTime.now(); }
}