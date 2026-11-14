package com.arena.core.students;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class StudentContracts {
  private StudentContracts() {}

  public record Request(
      @NotBlank @Size(max = 100) String firstName,
      @NotBlank @Size(max = 100) String lastName,
      @Past LocalDate dateOfBirth,
      @Size(max = 30) String gender,
      @Email @Size(max = 255) String email,
      @Size(max = 50) String phone,
      @Size(max = 160) String guardianName,
      @Size(max = 50) String guardianPhone,
      @Email @Size(max = 255) String guardianEmail,
      @Size(max = 1000) String address,
      @Size(max = 100) String emergencyContact,
      @Size(max = 1000) String notes,
    @Size(max = 50) List<@NotNull @Positive Long> sportIds,
      @Size(max = 16) String status,
      Long version) {}

  public record Response(
      Long id,
      String studentCode,
      String firstName,
      String lastName,
      LocalDate dateOfBirth,
      String gender,
      String email,
      String phone,
      String guardianName,
      String guardianPhone,
      String guardianEmail,
      String address,
      String emergencyContact,
      String notes,
      String status,
      List<Long> sportIds,
      LocalDateTime createdAt,
      Long version) {}
}