package com.arena.core.training;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
@Entity @Table(name="TRAINING_BATCH") @Getter @Setter
public class TrainingBatch extends TrainingRecord {
 @Column(name="academy_id") private Long academyId;
 private Long sportId;
 private String name;
 private String code;
 private String level;
 private Integer capacity;
 private java.time.LocalDate startDate;
 private java.time.LocalDate endDate;
 private String status;
 private Long leadCoachId;
 @ElementCollection(fetch=FetchType.EAGER)
 @CollectionTable(name="BATCH_COACH",joinColumns={@JoinColumn(name="batch_id",referencedColumnName="id"),@JoinColumn(name="academy_id",referencedColumnName="academy_id")})
 @Column(name="staff_id") private java.util.Set<Long> coachIds = new java.util.HashSet<>();
}
