package com.arena.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "APP_USER")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppUserEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "app_user_seq")
  @SequenceGenerator(name = "app_user_seq", sequenceName = "seq_app_user", allocationSize = 1)
  @Column(name = "ID")
  private Long id;

  @Column(name = "USERNAME", nullable = false, length = 100)
  private String username;

  @Column(name = "EMAIL", length = 255)
  private String email;

  @Column(name = "DISPLAY_NAME", length = 160)
  private String displayName;

  @Column(name = "PASSWORD_HASH", nullable = false, length = 255)
  private String passwordHash;

  @Column(name = "ROLE", nullable = false, length = 50)
  private String role;

  @Column(name = "CUSTOMER_ID")
  private Long customerId;

  @Column(name = "IS_ACTIVE", nullable = false)
  @Builder.Default
  private Boolean isActive = true;

  @Column(name = "LAST_SEEN_AT")
  private LocalDateTime lastSeenAt;

  @Column(name = "LOGGED_OUT_AT")
  private LocalDateTime loggedOutAt;

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
