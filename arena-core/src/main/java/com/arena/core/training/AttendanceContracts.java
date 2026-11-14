package com.arena.core.training;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public final class AttendanceContracts {
  private AttendanceContracts() {}
  public record Capture(@NotBlank String credential, @NotNull @Positive Long sessionId) {}
  public record Manual(@NotNull @Positive Long studentId, @NotNull @Positive Long sessionId, @NotBlank String status, String note) {}
  public record Confirm(@NotBlank String status, String note) {}
  public record Credential(Long id, String kind, String value, String message) {}
  public record AttendanceView(Long id, Long studentId, String studentCode, String studentName, Long sessionId, String status, String captureMethod, java.time.LocalDateTime capturedAt, java.time.LocalDateTime confirmedAt, String note) {}
  public record SessionStudent(Long studentId, String studentCode, String firstName, String lastName, Long attendanceId, String status) {}
}