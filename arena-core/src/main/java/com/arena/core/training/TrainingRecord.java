package com.arena.core.training;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
@MappedSuperclass @Getter @Setter
public abstract class TrainingRecord {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Version private Long version;
 private LocalDateTime createdAt;
 private LocalDateTime updatedAt;
 private String createdBy;
 private String updatedBy;
 @PrePersist void created() { createdAt=LocalDateTime.now(); updatedAt=createdAt; }
 @PreUpdate void updated() { updatedAt=LocalDateTime.now(); }
}
