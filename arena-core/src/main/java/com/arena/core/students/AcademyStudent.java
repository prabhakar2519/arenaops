package com.arena.core.students;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "academy_student")
@Getter
@Setter
public class AcademyStudent {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Version
  private Long version;

  @Column(name = "academy_id", nullable = false)
  private Long academyId;

  @Column(name = "student_code", nullable = false, length = 24)
  private String studentCode;

  @Column(name = "first_name", nullable = false, length = 100)
  private String firstName;

  @Column(name = "last_name", nullable = false, length = 100)
  private String lastName;

  @Column(name = "date_of_birth")
  private LocalDate dateOfBirth;

  @Column(length = 30)
  private String gender;

  @Column(length = 255)
  private String email;

  @Column(length = 50)
  private String phone;

  @Column(name = "guardian_name", length = 160)
  private String guardianName;

  @Column(name = "guardian_phone", length = 50)
  private String guardianPhone;

  @Column(name = "guardian_email", length = 255)
  private String guardianEmail;

  @Column(length = 1000)
  private String address;

  @Column(name = "emergency_contact", length = 100)
  private String emergencyContact;

  @Column(length = 1000)
  private String notes;

  @Column(nullable = false, length = 16)
  private String status;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @Column(name = "created_by", length = 100)
  private String createdBy;

  @Column(name = "updated_by", length = 100)
  private String updatedBy;

  @PrePersist
  void onCreate() {
    LocalDateTime now = LocalDateTime.now();
    createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = LocalDateTime.now();
  }
}