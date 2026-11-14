package com.arena.core.publicclub;
import jakarta.persistence.*;import java.time.Instant;import lombok.Getter;import lombok.Setter;
@Entity @Table(name="club_customer",uniqueConstraints=@UniqueConstraint(columnNames={"academyId","identityKey"})) @Getter @Setter
public class ClubCustomer {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY)private Long id;
 @Column(nullable=false)private Long academyId;
 @Column(nullable=false,length=64)private String identityKey;
 @Column(length=100)private String name;
 @Column(length=16)private String mobile;
 @Column(nullable=false,length=16)private String verification="UNVERIFIED";
 @Column(nullable=false)private Instant createdAt;
 @Column(nullable=false)private Instant updatedAt;
 private Instant deletedAt;
}
