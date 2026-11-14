package com.arena.core.checkin;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name="facility_qr_challenge") @Getter @Setter
public class FacilityQrChallenge {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false) private Long academyId;
 @Column(nullable=false) private Long facilityId;
 @Column(nullable=false,unique=true,length=64) private String tokenHash;
 @Column(nullable=false) private Instant expiresAt;
 private Instant usedAt;
 private Instant createdAt;
 private Long permanentQrId;
}
