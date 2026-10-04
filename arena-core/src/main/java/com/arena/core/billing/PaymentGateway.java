package com.arena.core.billing;

import java.math.BigDecimal;
import java.util.UUID;

/** Provider boundary: future adapters initiate checkout and verify callbacks server-side. */
public interface PaymentGateway {
  /** A result constructed only after server-side provider signature/status verification. */
  record VerifiedPayment(UUID attemptId, String providerReference, BigDecimal amount, String currency, BillingTypes.Outcome outcome) {}
  record Session(String provider, String reference) {}
  /** Creates a provider session using the persisted authoritative amount. */
  Session initiate(UUID attemptId, BigDecimal amount, String currency);
  /** Indicates whether this adapter exposes a development simulator. */
  default boolean mockEnabled() { return false; }
  /** Verifies a simulated result; real adapters must reject browser-supplied outcomes. */
  default BillingTypes.Outcome verifyMock(BillingTypes.Outcome outcome) {
    throw new com.arena.core.exception.ArenaOpsException(com.arena.core.exception.ErrorCode.ACCESS_DENIED);
  }
}
