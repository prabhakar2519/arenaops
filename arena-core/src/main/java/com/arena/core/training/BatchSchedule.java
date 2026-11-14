package com.arena.core.training;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
@Entity @Table(name="BATCH_SCHEDULE") @Getter @Setter
public class BatchSchedule extends TrainingRecord {
 private Long batchId;
 private int dayOfWeek;
 private java.time.LocalTime startTime;
 private java.time.LocalTime endTime;
 private java.time.LocalDate effectiveFrom;
 private java.time.LocalDate effectiveTo;
 private boolean active;
}
