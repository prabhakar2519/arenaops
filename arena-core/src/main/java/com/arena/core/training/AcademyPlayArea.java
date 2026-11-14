package com.arena.core.training;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
@Entity @Table(name="ACADEMY_PLAY_AREA") @Getter @Setter
public class AcademyPlayArea extends TrainingRecord {
 private Long academyId;
 private Long sportId;
 private String name;
 private String code;
 private String kind;
 private Integer capacity;
 private boolean active;
 @Column(length=30) private String facilityType;
 @Column(length=160) private String displayName;
 @Column(precision=12,scale=2) private java.math.BigDecimal hourlyRate;
 @Column(precision=12,scale=2) private java.math.BigDecimal frameRate;
}
