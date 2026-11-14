package com.arena.core.checkin;
import jakarta.persistence.*;import java.time.Instant;import lombok.Getter;import lombok.Setter;
@Entity @Table(name="facility_permanent_qr") @Getter @Setter
public class FacilityPermanentQr {
 @Id private Long facilityId;
 @Column(nullable=false)private Long academyId;
 @Column(nullable=false,unique=true,length=43)private String publicId;
 @Column(nullable=false)private boolean revoked;
 private Instant suspendedUntil;
 private Instant quotaResetAt;
}
