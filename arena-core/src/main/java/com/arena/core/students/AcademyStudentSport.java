package com.arena.core.students;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "academy_student_sport")
@Getter
@Setter
public class AcademyStudentSport {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "academy_id", nullable = false)
  private Long academyId;

  @Column(name = "student_id", nullable = false)
  private Long studentId;

  @Column(name = "sport_id", nullable = false)
  private Long sportId;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  public AcademyStudentSport() {
    createdAt = LocalDateTime.now();
  }

  public AcademyStudentSport(Long academyId, Long studentId, Long sportId) {
    this();
    this.academyId = academyId;
    this.studentId = studentId;
    this.sportId = sportId;
  }
}