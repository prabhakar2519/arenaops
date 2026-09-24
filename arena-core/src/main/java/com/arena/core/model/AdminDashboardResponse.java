package com.arena.core.model;

import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminDashboardResponse {
  private long totalCustomers;
  private long activeCustomers;
  private long pendingRegistration;
  private long accessBlocked;
  private long paymentDue;
  private BigDecimal monthlyRecurringRevenue;
  private BigDecimal yearlyRecurringRevenue;
  private List<AdminCustomerResponse> recentCustomers;
}
