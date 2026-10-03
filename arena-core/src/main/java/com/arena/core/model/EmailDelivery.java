package com.arena.core.model;

import com.arena.core.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record EmailDelivery(Status status, ErrorCode errorCode, String description) {
  public enum Status { SENT, DISABLED, FAILED }
}
