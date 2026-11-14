package com.arena.core.publicclub;
import jakarta.persistence.*;import java.time.*;import java.util.UUID;import lombok.Getter;import lombok.Setter;
@Entity @Table(name="club_reservation_request",uniqueConstraints=@UniqueConstraint(columnNames={"academyId","requestId"})) @Getter @Setter
public class ClubReservationRequest {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY)private Long id;
 private Long academyId;private Long facilityId;private UUID requestId;
 @Column(length=64)private String requestHash;
 @Column(length=43,unique=true)private String reference;
 @Column(length=100)private String customerName;
 @Column(length=16)private String mobile;
 @Column(length=64)private String mobileHash;
 @Column(length=16)private String status;
 private LocalDateTime reservedStart;private LocalDateTime reservedEnd;
 private Instant createdAt;private Instant expiresAt;private Instant decidedAt;
 @Column(length=100)private String decidedBy;
 private Long sessionId;private Long customerId;
}
