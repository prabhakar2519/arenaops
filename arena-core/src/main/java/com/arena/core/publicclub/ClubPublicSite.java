package com.arena.core.publicclub;
import jakarta.persistence.*;
import java.time.*;
import lombok.Getter;import lombok.Setter;
@Entity @Table(name="club_public_site") @Getter @Setter
public class ClubPublicSite {
 @Id private Long academyId;
 @Version private Long version;
 @Column(nullable=false,length=40) private String slug;
 private boolean published;
 private Instant slugChangedAt;
 private boolean bookingsEnabled;
 private LocalTime opensAt;
 private LocalTime closesAt;
 @Column(length=100) private String durationMinutes;
}
