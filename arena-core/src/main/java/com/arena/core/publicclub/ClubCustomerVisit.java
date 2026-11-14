package com.arena.core.publicclub;
import jakarta.persistence.*;import java.time.Instant;import lombok.Getter;import lombok.Setter;
@Entity @Table(name="club_customer_visit") @Getter @Setter
public class ClubCustomerVisit {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY)private Long id;
 @Column(nullable=false)private Long academyId;
 @Column(nullable=false)private Long customerId;
 @Column(nullable=false,unique=true)private Long sessionId;
 @Column(nullable=false)private Instant startedAt;
}
