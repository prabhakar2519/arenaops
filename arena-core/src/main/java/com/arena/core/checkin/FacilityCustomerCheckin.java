package com.arena.core.checkin;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name="facility_customer_checkin") @Getter @Setter
public class FacilityCustomerCheckin {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false) private Long academyId;
 @Column(nullable=false) private Long facilityId;
 @Column(nullable=false,unique=true) private Long challengeId;
 @Column(length=100) private String customerName;
 @Column(length=16) private String mobile;
 @Column(nullable=false,length=16) private String status;
 @Column(nullable=false) private Instant createdAt;
 @Column(nullable=false) private Instant expiresAt;
 private Instant confirmedAt;
 @Column(length=100) private String confirmedBy;
 @Column(unique=true) private Long sessionId;
 private Long customerId;
 private java.util.UUID requestId;
 @Column(length=64)private String requestHash;
 @Column(length=64)private String mobileHash;
}
