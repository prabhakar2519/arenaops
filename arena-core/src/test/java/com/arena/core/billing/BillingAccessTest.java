package com.arena.core.billing;

import com.arena.core.controller.AccessController;
import com.arena.core.entity.AppUserEntity;
import com.arena.core.exception.*;
import com.arena.core.repository.AppUserRepository;
import com.arena.core.service.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class BillingAccessTest {
  @Test void expiredOwnerCanAuthenticateButStaffAndSuspendedOwnerRemainDenied() {
    var jwt = Jwt.withTokenValue("test").header("alg","none").subject("owner").build();
    var users = mock(AppUserRepository.class); var access = mock(CustomerAccessService.class);
    var authorization = new AdminAuthorizationService();
    var controller = new AccessController(users,authorization,access);
    var owner = AppUserEntity.builder().role("OWNER").isActive(true).customerId(1L).build();
    when(users.findByUsername("owner")).thenReturn(Optional.of(owner));
    for (var code : new ErrorCode[]{ErrorCode.PAYMENT_REQUIRED,ErrorCode.SUBSCRIPTION_EXPIRED}) {
      doThrow(new ArenaOpsException(code)).when(access).assertCustomerCanUseApp(owner);
      var body = controller.check(jwt).getBody();
      assertEquals(false,body.get("allowed")); assertEquals(true,body.get("billingAllowed"));
    }
    doThrow(new ArenaOpsException(ErrorCode.CUSTOMER_ACCESS_BLOCKED)).when(access).assertCustomerCanUseApp(owner);
    assertThrows(ArenaOpsException.class,() -> controller.check(jwt));
    owner.setRole("STAFF");
    doThrow(new ArenaOpsException(ErrorCode.PAYMENT_REQUIRED)).when(access).assertCustomerCanUseApp(owner);
    assertThrows(ArenaOpsException.class,() -> controller.check(jwt));
  }
}
