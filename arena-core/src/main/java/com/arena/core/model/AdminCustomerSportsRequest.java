package com.arena.core.model;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Data;

@Data
public class AdminCustomerSportsRequest {

  @NotEmpty(message = "Select a customer plan")
  private List<String> sports;
}
