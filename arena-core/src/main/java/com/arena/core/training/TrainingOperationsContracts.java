package com.arena.core.training;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.time.LocalTime;

public final class TrainingOperationsContracts {
  private TrainingOperationsContracts() {}
  public record Enroll(@NotNull @Positive Long studentId, @NotNull @Positive Long batchId, @NotNull LocalDate startDate, LocalDate endDate) {}
  public record EndEnrollment(LocalDate endDate) {}
  public record ResolveSession(@NotNull @Positive Long batchId, @NotNull LocalDate date) {}
  public record EnrollmentView(Long id, Long studentId, String studentCode, String studentName, Long batchId, String batchName, LocalDate startDate, LocalDate endDate, String status) {}
  public record SessionView(Long id, Long batchId, String batchName, Long sportId, LocalDate date, LocalTime startTime, LocalTime endTime, String status) {}
}