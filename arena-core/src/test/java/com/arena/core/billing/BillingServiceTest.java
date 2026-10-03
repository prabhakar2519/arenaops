package com.arena.core.billing;

import com.arena.core.entity.*;
import com.arena.core.enums.*;
import com.arena.core.exception.*;
import com.arena.core.repository.*;
import com.arena.core.service.AdminAuthorizationService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.springframework.security.oauth2.jwt.Jwt;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.arena.core.billing.BillingTypes.*;

class BillingServiceTest {
  AppUserRepository users = mock(AppUserRepository.class);
  CustomerRepository customers = mock(CustomerRepository.class);
  CustomerSubscriptionRepository subscriptions = mock(CustomerSubscriptionRepository.class);
  CustomerPaymentRepository payments = mock(CustomerPaymentRepository.class);
  BillingOrderRepository orders = mock(BillingOrderRepository.class);
  BillingAttemptRepository attempts = mock(BillingAttemptRepository.class);
  BillingPricing pricing = new BillingPricing(new BigDecimal("1000"), new BigDecimal("10000"), new BigDecimal("0.18"));
  BillingService service = new BillingService(mock(jakarta.persistence.EntityManager.class),users,customers,subscriptions,payments,orders,attempts,pricing,
      new MockPaymentGateway(),new AdminAuthorizationService("admin"));
  Jwt jwt = Jwt.withTokenValue("test").header("alg","none").subject("owner").claim("preferred_username","owner").build();
  CustomerEntity customer;
  CustomerSubscriptionEntity subscription;
  BillingOrderEntity order;
  BillingAttemptEntity attempt;

  @BeforeEach void setup() {
    customer = CustomerEntity.builder().id(1L).parlourName("Academy").customerStatus(CustomerStatus.ACTIVE).build();
    subscription = CustomerSubscriptionEntity.builder().id(2L).customerId(1L).plan("ACADEMY")
        .subscriptionStatus(SubscriptionStatus.TRIAL).trialStartedAt(LocalDateTime.now().minusDays(1))
        .trialEndsAt(LocalDateTime.now().plusDays(13)).build();
    when(users.findByUsername("owner")).thenReturn(Optional.of(AppUserEntity.builder().role("OWNER").isActive(true).customerId(1L).build()));
    when(customers.findBillingCustomerForUpdate(1L)).thenReturn(Optional.of(customer));
    when(subscriptions.findFirstByCustomerIdOrderByCreatedAtDesc(1L)).thenReturn(Optional.of(subscription));
    when(orders.save(any())).thenAnswer(i -> {
      order = i.getArgument(0);
      when(orders.findByIdAndCustomerId(order.getId(),1L)).thenReturn(Optional.of(order));
      return order;
    });
    when(attempts.save(any())).thenAnswer(i -> {
      attempt = i.getArgument(0);
      when(attempts.findByIdAndOrderId(attempt.getId(),attempt.getOrderId())).thenReturn(Optional.of(attempt));
      when(attempts.findFirstByOrderIdOrderByCreatedAtDesc(attempt.getOrderId())).thenReturn(Optional.of(attempt));
      return attempt;
    });
  }

  @Test void activeAndExpiredTrialsAndExpiredPaidTerm() {
    assertEquals("TRIAL",service.overview(jwt).status());
    assertEquals(13,service.overview(jwt).remainingTrialDays());
    subscription.setTrialEndsAt(LocalDateTime.now().minusDays(1));
    assertEquals("PAYMENT_DUE",service.overview(jwt).status());
    assertEquals(0,service.overview(jwt).remainingTrialDays());
    subscription.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
    subscription.setCurrentPeriodEnd(LocalDateTime.now().minusDays(1));
    assertEquals("EXPIRED",service.overview(jwt).status());
  }
  @Test void monthlyAndAnnualAmountsCalculatedByBackend() {
    var monthly = service.create(jwt,new CreateOrder("STANDARD",Cycle.MONTHLY,UUID.randomUUID()));
    assertEquals(new BigDecimal("1180.00"),monthly.total());
    assertEquals(new BigDecimal("180.00"),monthly.taxAmount());
    var annual = service.create(jwt,new CreateOrder("STANDARD",Cycle.ANNUAL,UUID.randomUUID()));
    assertEquals(new BigDecimal("11800.00"),annual.total());
    assertEquals(SubscriptionStatus.TRIAL,subscription.getSubscriptionStatus());
  }
  @Test void invalidPlanAndCancelledSubscriptionRejected() {
    assertCode(ErrorCode.INVALID_REQUEST,() -> service.create(jwt,new CreateOrder("fake",Cycle.MONTHLY,UUID.randomUUID())));
    subscription.setSubscriptionStatus(SubscriptionStatus.CANCELLED);
    assertCode(ErrorCode.INVALID_STATE,() -> service.create(jwt,new CreateOrder("STANDARD",Cycle.MONTHLY,UUID.randomUUID())));
  }
  @Test void duplicateCreationAndChangedSelectionConflict() {
    UUID key = UUID.randomUUID();
    var first = service.create(jwt,new CreateOrder("STANDARD",Cycle.MONTHLY,key));
    when(orders.findByCustomerIdAndRequestId(1L,key)).thenReturn(Optional.of(order));
    assertEquals(first.id(),service.create(jwt,new CreateOrder("STANDARD",Cycle.MONTHLY,key)).id());
    assertCode(ErrorCode.CONFLICT,() -> service.create(jwt,new CreateOrder("STANDARD",Cycle.ANNUAL,key)));
    verify(orders,times(1)).save(any());
  }
  @Test void pendingOrderPreventsSeparateDuplicateOrders() {
    service.create(jwt,new CreateOrder("STANDARD",Cycle.MONTHLY,UUID.randomUUID()));
    when(orders.findFirstByCustomerIdAndStatusInOrderByCreatedAtDesc(eq(1L),any())).thenReturn(Optional.of(order));
    assertCode(ErrorCode.CONFLICT,() -> service.create(jwt,new CreateOrder("STANDARD",Cycle.ANNUAL,UUID.randomUUID())));
  }
  @Test void successfulPaymentIsIdempotentAndPreservesSports() {
    start(Cycle.MONTHLY); UUID id = attempt.getId();
    service.mock(jwt,order.getId(),new MockResult(id,Outcome.PAID));
    LocalDateTime end = subscription.getCurrentPeriodEnd();
    service.mock(jwt,order.getId(),new MockResult(id,Outcome.PAID));
    assertEquals(end,subscription.getCurrentPeriodEnd());
    assertEquals(SubscriptionStatus.ACTIVE,subscription.getSubscriptionStatus());
    assertEquals("ACADEMY",subscription.getPlan());
    assertEquals(OrderStatus.PAID,order.getStatus());
    verify(payments,times(1)).save(any());
    assertCode(ErrorCode.INVALID_STATE,() -> service.checkout(jwt,order.getId()));
    assertCode(ErrorCode.INVALID_STATE,() -> service.mock(jwt,order.getId(),new MockResult(id,Outcome.FAILED)));
  }
  @Test void failedAttemptCanRetryAndSucceed() {
    start(Cycle.MONTHLY); UUID failedId = attempt.getId();
    service.mock(jwt,order.getId(),new MockResult(failedId,Outcome.FAILED));
    assertEquals(SubscriptionStatus.TRIAL,subscription.getSubscriptionStatus());
    verifyNoInteractions(payments);
    service.checkout(jwt,order.getId());
    assertNotEquals(failedId,attempt.getId());
    service.mock(jwt,order.getId(),new MockResult(attempt.getId(),Outcome.PAID));
    verify(payments,times(1)).save(any());
  }
  @Test void paymentCancellationAndPreCheckoutCancellationPreserveTrial() {
    start(Cycle.MONTHLY);
    service.mock(jwt,order.getId(),new MockResult(attempt.getId(),Outcome.CANCELLED));
    assertEquals(OrderStatus.CANCELLED,order.getStatus());
    assertEquals(SubscriptionStatus.TRIAL,subscription.getSubscriptionStatus());
    assertCode(ErrorCode.INVALID_STATE,() -> service.checkout(jwt,order.getId()));
    service.create(jwt,new CreateOrder("STANDARD",Cycle.MONTHLY,UUID.randomUUID()));
    assertEquals(OrderStatus.CANCELLED,service.cancel(jwt,order.getId()).status());
    verifyNoInteractions(payments);
  }
  @Test void annualRenewalExtendsFutureEndOnce() {
    subscription.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
    LocalDateTime end = LocalDateTime.now().plusDays(20);
    subscription.setCurrentPeriodEnd(end);
    start(Cycle.ANNUAL);
    service.mock(jwt,order.getId(),new MockResult(attempt.getId(),Outcome.PAID));
    assertEquals(end.plusYears(1),subscription.getCurrentPeriodEnd());
  }
  @Test void verifiedProviderResultChecksReferenceAndAmountAndIsIdempotent() {
    start(Cycle.MONTHLY);
    when(attempts.findById(attempt.getId())).thenReturn(Optional.of(attempt));
    when(orders.findById(order.getId())).thenReturn(Optional.of(order));
    var bad = new PaymentGateway.VerifiedPayment(attempt.getId(),attempt.getProviderReference(),BigDecimal.ONE,"INR",Outcome.PAID);
    assertCode(ErrorCode.INVALID_STATE,() -> service.processVerifiedPayment(bad));
    var verified = new PaymentGateway.VerifiedPayment(attempt.getId(),attempt.getProviderReference(),attempt.getAmount(),"INR",Outcome.PAID);
    service.processVerifiedPayment(verified); service.processVerifiedPayment(verified);
    verify(payments,times(1)).save(any());
  }
  @Test void duplicateCheckoutResumesSameAttempt() {
    start(Cycle.MONTHLY); UUID id = attempt.getId();
    assertEquals(id,service.checkout(jwt,order.getId()).attemptId());
    verify(attempts,times(1)).save(any());
  }
  @Test void amountMismatchExpiredOrderAndWrongAttemptRejectSuccess() {
    start(Cycle.MONTHLY);
    assertCode(ErrorCode.RESOURCE_NOT_FOUND,() -> service.mock(jwt,order.getId(),new MockResult(UUID.randomUUID(),Outcome.PAID)));
    attempt.setAmount(BigDecimal.ONE);
    assertCode(ErrorCode.INVALID_STATE,() -> service.mock(jwt,order.getId(),new MockResult(attempt.getId(),Outcome.PAID)));
    attempt.setAmount(order.getTotal()); order.setExpiresAt(LocalDateTime.now().minusSeconds(1));
    assertCode(ErrorCode.INVALID_STATE,() -> service.mock(jwt,order.getId(),new MockResult(attempt.getId(),Outcome.PAID)));
    verifyNoInteractions(payments);
  }
  @Test void foreignOrderCannotBeReadCheckedOutCancelledOrPaid() {
    UUID foreignId = UUID.randomUUID();
    BillingOrderEntity foreign = new BillingOrderEntity(); foreign.setId(foreignId); foreign.setCustomerId(99L);
    when(orders.findById(foreignId)).thenReturn(Optional.of(foreign));
    assertCode(ErrorCode.RESOURCE_NOT_FOUND,() -> service.get(jwt,foreignId));
    assertCode(ErrorCode.RESOURCE_NOT_FOUND,() -> service.checkout(jwt,foreignId));
    assertCode(ErrorCode.RESOURCE_NOT_FOUND,() -> service.cancel(jwt,foreignId));
    assertCode(ErrorCode.RESOURCE_NOT_FOUND,() -> service.mock(jwt,foreignId,new MockResult(UUID.randomUUID(),Outcome.PAID)));
    verifyNoInteractions(payments);
  }
  @Test void unauthorizedRoleDisabledUserMissingCustomerAndForeignOrder() {
    when(users.findByUsername("owner")).thenReturn(Optional.of(AppUserEntity.builder().role("STAFF").isActive(true).customerId(1L).build()));
    assertCode(ErrorCode.ACCESS_DENIED,() -> service.overview(jwt));
    when(users.findByUsername("owner")).thenReturn(Optional.of(AppUserEntity.builder().role("OWNER").isActive(false).customerId(1L).build()));
    assertCode(ErrorCode.ACCESS_DENIED,() -> service.overview(jwt));
    when(users.findByUsername("owner")).thenReturn(Optional.of(AppUserEntity.builder().role("OWNER").isActive(true).customerId(1L).build()));
    assertCode(ErrorCode.RESOURCE_NOT_FOUND,() -> service.get(jwt,UUID.randomUUID()));
    customer.setCustomerStatus(CustomerStatus.SUSPENDED);
    assertCode(ErrorCode.CUSTOMER_ACCESS_BLOCKED,() -> service.overview(jwt));
  }
  private void start(Cycle cycle) {
    var o = service.create(jwt,new CreateOrder("STANDARD",cycle,UUID.randomUUID()));
    service.checkout(jwt,o.id());
  }
  private void assertCode(ErrorCode code, Runnable action) {
    assertEquals(code,assertThrows(ArenaOpsException.class,action::run).getErrorCode());
  }
}
