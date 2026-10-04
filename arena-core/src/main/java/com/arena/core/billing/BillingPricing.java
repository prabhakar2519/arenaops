package com.arena.core.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.arena.core.exception.*;
import static com.arena.core.billing.BillingTypes.*;

/** Centralized configurable commercial pricing and tax policy. */
@Component
public class BillingPricing {
  private final BigDecimal monthly;
  private final BigDecimal annual;
  private final BigDecimal taxRate;
  /** Loads validated server-side prices; sample defaults require business approval. */
  public BillingPricing(@Value("${app.billing.monthly:${ARENAOPS_BILLING_MONTHLY:1000}}") BigDecimal monthly,
      @Value("${app.billing.annual:${ARENAOPS_BILLING_ANNUAL:10000}}") BigDecimal annual,
      @Value("${app.billing.tax-rate:${ARENAOPS_BILLING_TAX_RATE:0.18}}") BigDecimal taxRate) {
    if (monthly.signum() <= 0 || annual.signum() <= 0 || taxRate.signum() < 0 || taxRate.compareTo(BigDecimal.ONE) > 0)
      throw new IllegalArgumentException("Invalid billing configuration");
    this.monthly = monthly; this.annual = annual; this.taxRate = taxRate;
  }
  /** Returns selectable cycles with rounded authoritative totals. */
  public List<Plan> plans() { return List.of(price("STANDARD", Cycle.MONTHLY), price("STANDARD", Cycle.ANNUAL)); }
  /** Resolves a known commercial plan and computes GST once. */
  public Plan price(String id, Cycle cycle) {
    if (!"STANDARD".equals(id) || cycle == null) throw new ArenaOpsException(ErrorCode.INVALID_REQUEST);
    BigDecimal base = (cycle == Cycle.MONTHLY ? monthly : annual).setScale(2, RoundingMode.HALF_UP);
    BigDecimal tax = base.multiply(taxRate).setScale(2, RoundingMode.HALF_UP);
    return new Plan(id, "ArenaOps Standard", cycle, base, tax, base.add(tax), "INR");
  }
}
