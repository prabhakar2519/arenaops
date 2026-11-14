package com.arena.core.checkin;
import java.time.*;
import jakarta.validation.constraints.*;
public final class FacilityCheckinContracts {
 private FacilityCheckinContracts(){}
 public record Token(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{43}") String token){@Override public String toString(){return "Token[redacted]";}}
 public record Submission(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{43}") String token,
  @NotBlank @Size(max=100) String name,@NotBlank @Size(max=40) String mobile,@AssertTrue boolean privacyAccepted,@NotNull java.util.UUID requestId,@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{43}")String formToken,@Size(max=0)String website){
 public Submission(String token,String name,String mobile,boolean accepted){this(token,name,mobile,accepted,null,null,null);}@Override public String toString(){return "Submission[redacted]";}}
 public record Challenge(String token,String facility,Instant expiresAt,String url){public Challenge(String token,String facility,Instant expiresAt){this(token,facility,expiresAt,null);}@Override public String toString(){return "Challenge[token=redacted,expiresAt="+expiresAt+"]";}}
 public record PublicInfo(String facility,Instant expiresAt,String formToken){public PublicInfo(String facility,Instant expiresAt){this(facility,expiresAt,null);}}
 public record Receipt(String status,Instant expiresAt){}
 public record Pending(Long id,Long facilityId,String facility,String name,String mobile,boolean mobileVerified,Instant createdAt,Instant expiresAt){@Override public String toString(){return "Pending[id="+id+",personalDetails=redacted]";}}
 public record Confirm(LocalDateTime plannedEndAt){}
 public record Confirmation(Long sessionId,String status){}
}
