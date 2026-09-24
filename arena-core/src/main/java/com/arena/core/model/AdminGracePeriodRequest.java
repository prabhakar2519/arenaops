package com.arena.core.model;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class AdminGracePeriodRequest {

  @NotNull
  @Future
  private LocalDateTime endsAt;

  private String reason;
}
