package com.arena.core.model;

import lombok.Data;

@Data
public class AdminStateChangeRequest {
  @io.swagger.v3.oas.annotations.media.Schema(types={"string","null"})
  private String reason;
}
