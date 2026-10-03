package com.arena.core.billing;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import com.arena.core.exception.ArenaOpsException;
import static org.junit.jupiter.api.Assertions.*;

class PaymentGatewayConfigurationTest {
  @Test void onlyExplicitLocalOrDevEnablesMock() {
    for (String[] profiles : new String[][]{{}, {"prod"}, {"production"}, {"dev","prod"}, {"local","PRODUCTION"}}) {
      var env = new MockEnvironment(); env.setActiveProfiles(profiles);
      var gateway = new PaymentGatewayConfiguration().paymentGateway(env);
      assertFalse(gateway.mockEnabled());
      assertThrows(ArenaOpsException.class,() -> gateway.verifyMock(BillingTypes.Outcome.PAID));
      assertThrows(ArenaOpsException.class,() -> gateway.initiate(UUID.randomUUID(),BigDecimal.ONE,"INR"));
    }
    for (String profile : new String[]{"dev","local"}) {
      var env = new MockEnvironment(); env.setActiveProfiles(profile);
      assertTrue(new PaymentGatewayConfiguration().paymentGateway(env).mockEnabled());
    }
  }
  @Test void invalidPriceConfigurationFailsStartup() {
    assertThrows(IllegalArgumentException.class,() -> new BillingPricing(BigDecimal.ZERO,BigDecimal.TEN,BigDecimal.ZERO));
    assertThrows(IllegalArgumentException.class,() -> new BillingPricing(BigDecimal.TEN,BigDecimal.TEN,new BigDecimal("1.1")));
  }
}
