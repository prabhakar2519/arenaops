package com.arena.core.service;

import com.arena.core.entity.*;
import com.arena.core.enums.*;
import com.arena.core.exception.*;
import com.arena.core.repository.*;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class CustomerAccessServiceTest {
  @Test
  void trialExpiryCancellationAndSuspensionProduceDistinct403CodesWithoutChangingEntitlements() {
    var customers = mock(CustomerRepository.class);
    var subscriptions = mock(CustomerSubscriptionRepository.class);
    var overrides = mock(AccessOverrideRepository.class);
    var service = new CustomerAccessService(customers, subscriptions, overrides);
    var customer = CustomerEntity.builder().id(1L).customerStatus(CustomerStatus.ACTIVE).build();
    var subscription = CustomerSubscriptionEntity.builder().id(2L).customerId(1L)
        .subscriptionStatus(SubscriptionStatus.TRIAL).trialEndsAt(LocalDateTime.now().minusDays(1)).build();
    var user = AppUserEntity.builder().role("OWNER").customerId(1L).isActive(true).build();
    when(customers.findById(1L)).thenReturn(Optional.of(customer));
    when(subscriptions.findFirstByCustomerIdOrderByCreatedAtDesc(1L)).thenReturn(Optional.of(subscription));
    when(overrides.findFirstByCustomerIdAndStatusAndEndsAtAfterOrderByEndsAtDesc(any(), any(), any())).thenReturn(Optional.empty());
    assertEquals(ErrorCode.PAYMENT_REQUIRED, assertThrows(ArenaOpsException.class,
        () -> service.assertCustomerCanUseApp(user)).getErrorCode());
    assertEquals(SubscriptionStatus.PAYMENT_DUE, subscription.getSubscriptionStatus());
    subscription.setSubscriptionStatus(SubscriptionStatus.CANCELLED);
    assertEquals(ErrorCode.SUBSCRIPTION_ACCESS_BLOCKED, assertThrows(ArenaOpsException.class,
        () -> service.assertCustomerCanUseApp(user)).getErrorCode());
    customer.setCustomerStatus(CustomerStatus.SUSPENDED);
    assertEquals(ErrorCode.CUSTOMER_ACCESS_BLOCKED, assertThrows(ArenaOpsException.class,
        () -> service.assertCustomerCanUseApp(user)).getErrorCode());
  }
}
