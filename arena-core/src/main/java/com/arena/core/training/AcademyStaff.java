package com.arena.core.training;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
@Entity @Table(name="ACADEMY_STAFF") @Getter @Setter
public class AcademyStaff extends TrainingRecord {
 @Column(name="academy_id") private Long academyId;
 // Optional existing authenticated-user link; ordinary registration leaves this null.
 private Long appUserId;
 private String designation="GENERAL_STAFF";
 private String managerAccess="STANDARD";
 private boolean discountEnabled=false;
 @Column(precision=5,scale=2) private java.math.BigDecimal maxDiscountPercent=java.math.BigDecimal.ZERO;
 @Column(precision=12,scale=2) private java.math.BigDecimal maxDiscountAmount=java.math.BigDecimal.ZERO;
 @Column(precision=12,scale=2) private java.math.BigDecimal dailyDiscountAllowance;
 private boolean discountApprovalAllowed=false;
 private String keycloakSubject;
 private String loginState="NOT_ENABLED";
 private String notificationState="NOT_SENT";
 private java.time.LocalDateTime invitationExpiresAt;
 private java.time.LocalDateTime invitationSentAt;
 private String loginUsername;
 // Durable intent for a combined login-enabled registration; no employment activation until linked.
 @com.fasterxml.jackson.annotation.JsonIgnore private String registrationHash;
 private String registrationRequestId;
 private String loginEmail;
 private java.time.LocalDateTime accessValidAfter;
 @ElementCollection(fetch=FetchType.EAGER)
 @org.hibernate.annotations.Fetch(org.hibernate.annotations.FetchMode.SELECT)
 @CollectionTable(name="MEMBERSHIP_ROLE",joinColumns=@JoinColumn(name="staff_id"))
 @Column(name="role") private java.util.Set<String> applicationRoles=new java.util.HashSet<>();
 public String getLoginAccessStatus() {
  if("PENDING".equals(loginState) && invitationExpiresAt!=null && invitationExpiresAt.isBefore(java.time.LocalDateTime.now())) return "INVITATION_EXPIRED";
  return "PENDING".equals(loginState)?"INVITATION_PENDING":loginState;
 }
 private String name;
 private String email;
 private String phone;
 private String role;
 private boolean active;
 @ElementCollection(fetch=FetchType.EAGER)
 @CollectionTable(name="ACADEMY_STAFF_SPORT",joinColumns={@JoinColumn(name="staff_id", referencedColumnName="id"),@JoinColumn(name="academy_id",referencedColumnName="academy_id")})
 @Column(name="sport_id") private java.util.Set<Long> sportIds = new java.util.HashSet<>();
}
