package com.arena.core.training;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;

public final class TrainingRequests {
 private TrainingRequests() {}
 public record Settings(@NotNull Long version, @NotBlank @Size(max=80) String timezone, boolean skipped) {}
 public record Sport(@NotBlank @Size(max=64) String catalogueCode,@io.swagger.v3.oas.annotations.media.Schema(types={"string","null"}) @Size(max=160) String customName, boolean training, boolean sessions, @io.swagger.v3.oas.annotations.media.Schema(description="Omit for active registration; false registers an inactive activity.",defaultValue="true") Boolean active) {
  public Sport(String catalogueCode,String customName,boolean training,boolean sessions){this(catalogueCode,customName,training,sessions,null);}
 }
 public record Activities(@NotEmpty @Size(max=50) List<@NotNull @Valid Activity> activities) {}
 public record Activity(@NotBlank @Size(max=64) String catalogueCode,@io.swagger.v3.oas.annotations.media.Schema(types={"string","null"}) @Size(max=160) String customName, @io.swagger.v3.oas.annotations.media.Schema(description="Omit for active registration; false registers an inactive activity.",defaultValue="true") Boolean active) {
  public Activity(String catalogueCode,String customName){this(catalogueCode,customName,null);}
 }
 public record Rename(@NotNull Long version,@NotBlank @Size(max=160) String name) {}
 public record SportUpdate(@NotNull Long version, boolean training, boolean sessions, boolean active) {}
 public record Staff(@io.swagger.v3.oas.annotations.media.Schema(types={"string","null"}) @Size(max=100) String username,
  @NotBlank @Size(max=160) String name,@io.swagger.v3.oas.annotations.media.Schema(types={"string","null"}) @Email @Size(max=255) String email,@io.swagger.v3.oas.annotations.media.Schema(types={"string","null"}) @Size(max=50) String phone, @NotBlank @Pattern(regexp="COACH|STAFF") String role,@io.swagger.v3.oas.annotations.media.Schema(types={"string","null"}) @Pattern(regexp="MANAGER|COACH|RECEPTIONIST|GENERAL_STAFF") String designation) {
  public Staff(String username,String name,String email,String phone,String role){this(username,name,email,phone,role,null);}
 }
 public record StaffUpdate(@NotNull Long version, @NotBlank @Size(max=160) String name,@io.swagger.v3.oas.annotations.media.Schema(types={"string","null"}) @Email @Size(max=255) String email,@io.swagger.v3.oas.annotations.media.Schema(types={"string","null"}) @Size(max=50) String phone, @NotBlank @Pattern(regexp="COACH|STAFF") String role, boolean active,@io.swagger.v3.oas.annotations.media.Schema(types={"string","null"}) @Pattern(regexp="MANAGER|COACH|RECEPTIONIST|GENERAL_STAFF") String designation) {
  public StaffUpdate(Long version,String name,String email,String phone,String role,boolean active){this(version,name,email,phone,role,active,null);}
 }
 public record AssignSports(@NotNull Long version, @NotNull @Size(max=100) Set<@NotNull Long> sportIds) {}
 public record Batch(@io.swagger.v3.oas.annotations.media.Schema(types={"integer","null"}) Long version, @NotNull Long sportId, @NotBlank @Size(max=160) String name,
  @NotBlank @Pattern(regexp="[A-Za-z0-9_-]+") @Size(max=64) String code,@io.swagger.v3.oas.annotations.media.Schema(types={"string","null"}) @Size(max=100) String level,@io.swagger.v3.oas.annotations.media.Schema(types={"integer","null"}) @Positive Integer capacity, @NotNull LocalDate startDate,@io.swagger.v3.oas.annotations.media.Schema(types={"string","null"}) LocalDate endDate,
  @NotBlank @Pattern(regexp="DRAFT|ACTIVE|INACTIVE") String status) {}
 public record Coaches(@NotNull Long version, @NotNull @Size(max=100) Set<@NotNull Long> coachIds,@io.swagger.v3.oas.annotations.media.Schema(types={"integer","null"}) Long leadCoachId) {}
 public record Weekly(@Min(1) @Max(7) int dayOfWeek, @NotNull LocalTime startTime,
  @NotNull LocalTime endTime, @NotNull LocalDate effectiveFrom,@io.swagger.v3.oas.annotations.media.Schema(types={"string","null"}) LocalDate effectiveTo) {}
 public record Schedules(@NotNull Long version, @NotNull @Size(max=100) List<@Valid Weekly> schedules) {}
}
