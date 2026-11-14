package com.arena.core.checkin;
import jakarta.persistence.*;import java.time.Instant;import lombok.Getter;import lombok.Setter;
@Entity @Table(name="facility_mobile_limit",uniqueConstraints=@UniqueConstraint(columnNames={"facilityId","mobileHash"})) @Getter @Setter
public class FacilityMobileLimit {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY)private Long id;
 private Long facilityId;
 @Column(length=64)private String mobileHash;
 private Instant blockedUntil;
}
