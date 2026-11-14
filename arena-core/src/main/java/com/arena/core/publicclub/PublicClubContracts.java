package com.arena.core.publicclub;
import java.time.*;import java.util.*;import jakarta.validation.constraints.*;
public final class PublicClubContracts {
 private PublicClubContracts(){}
 public record Settings(@NotNull Long version,@NotBlank @Size(max=40)String slug,boolean published,boolean acknowledgeSlugChange,boolean bookingsEnabled,LocalTime opensAt,LocalTime closesAt,@Size(max=12)List<@Min(15) @Max(240)Integer> durations){}
 public record SiteSettings(Long version,String slug,String url,boolean published,Instant slugChangeAllowedAt,boolean bookingsEnabled,LocalTime opensAt,LocalTime closesAt,List<Integer> durations,String previewUrl,boolean pathPreviewEnabled,boolean publicHostingReady){}
 public record Sport(String name,String code){}
 public record Facility(Long id,String label,String sport){}
 public record Site(String name,String logo,String cover,List<Sport> sports,String email,String phone,String address,String timezone,List<Facility> facilities,boolean bookingsEnabled,LocalTime opensAt,LocalTime closesAt,List<Integer> durations){}
 public record Booking(@NotNull(message="Refresh the booking form and try again.") UUID requestId,
  @NotNull(message="Choose a facility.") Long facilityId,
  @NotNull(message="Choose a date and start time in the club timezone.") LocalDateTime start,
  @NotNull(message="Choose a duration.") @Min(value=15,message="Choose one of the available durations.") @Max(value=240,message="Choose one of the available durations.") Integer duration,
  @NotBlank(message="Enter your name.") @Size(max=100,message="Use at most 100 characters for your name.") String name,
  @NotBlank(message="Enter your mobile number with its country code.") @Size(max=40,message="Use at most 40 characters for your mobile number.") String mobile,
  @AssertTrue(message="Accept the operational privacy notice to continue.") boolean privacyAccepted,
  @Size(max=0,message="Refresh the booking form and try again.") String website){@Override public String toString(){return "Booking[redacted]";}}
 public record BookingReceipt(String status,String reference){}
 public record BookingPending(Long id,String facility,String name,String mobile,LocalDateTime start,LocalDateTime end,Instant expiresAt){@Override public String toString(){return "BookingPending[id="+id+",personalDetails=redacted]";}}
}
