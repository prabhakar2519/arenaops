package com.arena.core.publicclub;
import jakarta.persistence.*;import lombok.Getter;import lombok.Setter;
@Entity @Table(name="club_public_slug") @Getter @Setter
public class ClubPublicSlug {
 @Id @Column(length=40) private String slug;
 @Column(nullable=false) private Long academyId;
}
