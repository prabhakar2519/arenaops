package com.arena.core.sessions;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class RunningSessionContracts {
  private RunningSessionContracts() {}
  public record Start(@NotNull @Positive Long facilityId, @Size(max = 160) String playerName, LocalDateTime plannedEndAt, @Size(max=40) String mobile) {
    public Start(Long facilityId,String playerName,LocalDateTime plannedEndAt){this(facilityId,playerName,plannedEndAt,null);}
    @Override public String toString(){return "Start[facilityId="+facilityId+",customerDetails=redacted]";}
    public Start(Long facilityId, String playerName) { this(facilityId, playerName, null, null); }
  }
  public record Reserve(@NotNull @Positive Long facilityId, @Size(max = 160) String playerName,
      @NotNull LocalDateTime reservedStart, @NotNull LocalDateTime reservedEnd) {}
  public record Stop(@DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal paidAmount,
      @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal discountAmount,
      @Positive Integer manualDurationMinutes, boolean quickStop, boolean markUnsettled,
      @NotNull @jakarta.validation.constraints.Pattern(regexp="[A-Za-z0-9_-]{43}") String quoteToken,@Size(max=500)String discountReason) {
    public Stop(BigDecimal paidAmount,BigDecimal discountAmount,Integer manualDurationMinutes,boolean quickStop,boolean markUnsettled){this(paidAmount,discountAmount,manualDurationMinutes,quickStop,markUnsettled,null,null);}
  }
  public record SessionView(Long id, Long facilityId, String facilityCode, String facilityName, Long sportId,
      String sport, String facilityType, String status, LocalDateTime startedAt, LocalDateTime endedAt,
    LocalDateTime stoppedAt, LocalDateTime pausedAt, Long durationSeconds, Long totalPausedSeconds, BigDecimal ratePerHour,
      BigDecimal currentAmount, BigDecimal totalAmount, BigDecimal paidAmount, BigDecimal outstandingAmount,
      String paymentStatus, String playerName, String startedBy, String stoppedBy, String stopType,
      LocalDateTime reservedStart, LocalDateTime reservedEnd, LocalDateTime plannedEndAt,BigDecimal originalAmount,BigDecimal approvedDiscount,BigDecimal requestedDiscount,String discountStatus,String discountReason,Long discountRequestId,String financialStatus,boolean financialVisible,String catalogueCode,String playerMobile) {}
  public record FacilityOption(Long id, Long sportId, String sport, String sportName, String code, String name,
      String facilityType, boolean active, BigDecimal hourlyRate, BigDecimal frameRate, boolean configured, boolean available) {}
  public record FacilitySummary(Long sportId, String sport, String sportName, long activeFacilities,
      long occupiedFacilities, long availableFacilities) {}
  public record DailyCollection(Long sportId, String sport, String sportName, BigDecimal charged,
      BigDecimal collected, BigDecimal pending) {}
  public record Dashboard(java.util.List<SessionView> running, java.util.List<SessionView> recent,
      java.util.List<FacilityOption> facilities, BigDecimal currentCharges, BigDecimal chargesToday,
      BigDecimal collectedToday, BigDecimal outstandingToday, java.util.List<FacilitySummary> facilitySummary,
      java.util.List<DailyCollection> collectionsBySport, java.util.List<SessionView> reserved,
      java.util.List<SessionView> unsettled) {}
}