package com.arena.core.billing;

import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import java.util.Arrays;
import java.math.BigDecimal;
import java.util.UUID;
import com.arena.core.exception.*;

/** Fails closed unless explicitly local/dev and never enables mock in production profiles. */
@Configuration
public class PaymentGatewayConfiguration {
  /** Selects the simulator only for an allowed profile with no production profile present. */
  @Bean
  public PaymentGateway paymentGateway(Environment environment) {
    var profiles = Arrays.asList(environment.getActiveProfiles());
    boolean enabled = profiles.stream().anyMatch(p -> p.equals("dev") || p.equals("local"))
        && profiles.stream().noneMatch(p -> p.equalsIgnoreCase("prod") || p.equalsIgnoreCase("production"));
    return enabled ? new MockPaymentGateway() : new UnavailableGateway();
  }
  /** Production placeholder until a verified real provider is installed. */
  private static class UnavailableGateway implements PaymentGateway {
    /** Prevents starting a payment without a configured provider. */
    public Session initiate(UUID id, BigDecimal amount, String currency) {
      throw new ArenaOpsException(ErrorCode.SERVICE_UNAVAILABLE);
    }
  }
}
