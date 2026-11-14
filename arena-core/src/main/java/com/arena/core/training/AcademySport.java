package com.arena.core.training;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
@Entity @Table(name="ACADEMY_SPORT",uniqueConstraints=@UniqueConstraint(columnNames={"academy_id","registration_key"})) @Getter @Setter
public class AcademySport extends TrainingRecord {
 @Column(name="academy_id") private Long academyId;
 private String catalogueCode;
 private String name;
 @Column(name="registration_key") private String registrationKey;
 private boolean training;
 private boolean sessions;
 private boolean active;
}
