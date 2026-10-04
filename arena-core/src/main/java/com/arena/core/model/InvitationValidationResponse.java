package com.arena.core.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InvitationValidationResponse {
  private Long customerId;
  private String organizationName;
  private String invitedEmail;
  private String plan;
  private Integer trialDurationDays;
  private String invitationStatus;
}
