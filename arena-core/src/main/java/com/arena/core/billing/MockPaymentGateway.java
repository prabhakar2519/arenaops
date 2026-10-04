package com.arena.core.billing;

import java.math.BigDecimal;
import java.util.UUID;

/** Credential-free simulator instantiated only by the guarded gateway configuration. */
final class MockPaymentGateway implements PaymentGateway {
  /** Uses the attempt identifier as a stable mock provider reference. */
  public Session initiate(UUID id, BigDecimal amount, String currency) { return new Session("MOCK", "mock-" + id); }
  /** Allows the UI to display simulator controls. */
  public boolean mockEnabled() { return true; }
  /** Returns a simulated provider outcome only for this development adapter. */
  public BillingTypes.Outcome verifyMock(BillingTypes.Outcome outcome) { return outcome; }
}
