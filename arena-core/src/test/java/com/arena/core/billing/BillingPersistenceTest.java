package com.arena.core.billing;

import com.arena.core.entity.*;
import com.arena.core.enums.*;
import com.arena.core.repository.*;
import com.arena.core.service.AdminAuthorizationService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.arena.core.billing.BillingTypes.*;

/** Opt-in real PostgreSQL tests exercise the complete Liquibase chain and transactional service. */
@DataJpaTest
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Import({BillingService.class,BillingPricing.class,PaymentGatewayConfiguration.class,AdminAuthorizationService.class})
@TestPropertySource(properties={"spring.datasource.url=${ARENA_BILLING_DB_URL}",
    "spring.datasource.username=${ARENA_BILLING_DB_USER:arena}", "spring.datasource.password=${ARENA_BILLING_DB_PASSWORD:arena}",
    "spring.jpa.hibernate.ddl-auto=validate", "spring.profiles.active=local", "spring.liquibase.default-schema=public",
    "spring.jpa.show-sql=false", "spring.mail.username=", "spring.mail.password="})
@EnabledIfEnvironmentVariable(named="ARENA_BILLING_DB_URL",matches=".+")
class BillingPersistenceTest {
  @Autowired BillingService billing;
  @Autowired CustomerRepository customers;
  @Autowired CustomerSubscriptionRepository subscriptions;
  @Autowired AppUserRepository users;
  @Autowired BillingOrderRepository orders;
  @Autowired BillingAttemptRepository attempts;
  @Autowired CustomerPaymentRepository payments;
  @Autowired EntityManager entityManager;
  CustomerEntity customer;
  org.springframework.security.oauth2.jwt.Jwt jwt;

  @BeforeEach void setup() {
    LocalDate today = LocalDate.now();
    String name = "billing-test-" + UUID.randomUUID();
    customer = customers.saveAndFlush(CustomerEntity.builder().customerName(name).parlourName(name).sports("ACADEMY")
        .onboardingDate(today).trialStartsAt(today).trialEndsAt(today.plusDays(14)).subscriptionType("MONTHLY")
        .billingAmount(BigDecimal.ZERO).billingStartDate(today).nextDueDate(today.plusDays(14)).paymentStatus("NOT_DUE")
        .status("ACTIVE").customerStatus(CustomerStatus.ACTIVE).accessStatus(AccessStatus.ALLOWED).build());
    subscriptions.saveAndFlush(CustomerSubscriptionEntity.builder().customerId(customer.getId()).plan("ACADEMY")
        .subscriptionStatus(SubscriptionStatus.TRIAL).trialDurationDays(14).trialStartedAt(LocalDateTime.now())
        .trialEndsAt(LocalDateTime.now().plusDays(14)).build());
    users.saveAndFlush(AppUserEntity.builder().username(name).email(name + "@example.test").role("OWNER").passwordHash("unused-test-hash")
        .customerId(customer.getId()).isActive(true).build());
    jwt = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("test").header("alg","none").subject(name).build();
  }

  @Test void migrationAndLedgerActivationPersistWithTenantIsolation() {
    var o = billing.create(jwt,new CreateOrder("STANDARD",Cycle.ANNUAL,UUID.randomUUID()));
    var pending = billing.checkout(jwt,o.id());
    billing.mock(jwt,o.id(),new MockResult(pending.attemptId(),Outcome.PAID));
    entityManager.flush(); entityManager.clear();
    assertEquals(OrderStatus.PAID,orders.findById(o.id()).orElseThrow().getStatus());
    assertEquals(SubscriptionStatus.ACTIVE,subscriptions.findFirstByCustomerIdOrderByCreatedAtDesc(customer.getId()).orElseThrow().getSubscriptionStatus());
    assertEquals("STANDARD",subscriptions.findFirstByCustomerIdOrderByCreatedAtDesc(customer.getId()).orElseThrow().getBillingPlanId());
    assertTrue(orders.findByIdAndCustomerId(o.id(),customer.getId()+100000).isEmpty());
    assertEquals(new BigDecimal("11800.00"),payments.findAll().stream().filter(p -> p.getCustomerId().equals(customer.getId())).findFirst().orElseThrow().getAmount());
  }
  @Test void databaseRejectsDuplicateRequestKeys() {
    UUID key = UUID.randomUUID();
    var o = billing.create(jwt,new CreateOrder("STANDARD",Cycle.MONTHLY,key));
    entityManager.flush();
    var duplicate = orders.findById(o.id()).orElseThrow(); entityManager.detach(duplicate);
    duplicate.setId(UUID.randomUUID());
    assertThrows(org.springframework.dao.DataIntegrityViolationException.class,() -> orders.saveAndFlush(duplicate));
  }
  @Test void databaseRejectsIncorrectOrderTotal() {
    var o = billing.create(jwt,new CreateOrder("STANDARD",Cycle.MONTHLY,UUID.randomUUID()));
    entityManager.flush();
    var persisted = orders.findById(o.id()).orElseThrow(); persisted.setTotal(BigDecimal.ONE);
    assertThrows(org.springframework.dao.DataIntegrityViolationException.class,() -> orders.saveAndFlush(persisted));
  }
  @Test void staleEntitlementCannotOverwriteActivation() {
    var stale = subscriptions.findFirstByCustomerIdOrderByCreatedAtDesc(customer.getId()).orElseThrow();
    entityManager.detach(stale);
    var o = billing.create(jwt,new CreateOrder("STANDARD",Cycle.MONTHLY,UUID.randomUUID()));
    var pending = billing.checkout(jwt,o.id()); billing.mock(jwt,o.id(),new MockResult(pending.attemptId(),Outcome.PAID));
    entityManager.flush(); entityManager.clear();
    stale.setSubscriptionStatus(SubscriptionStatus.PAYMENT_DUE);
    assertThrows(org.springframework.dao.OptimisticLockingFailureException.class,() -> subscriptions.saveAndFlush(stale));
  }
  @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
  void simultaneousSuccessRequestsActivateExactlyOnce() throws Exception {
    var o = billing.create(jwt,new CreateOrder("STANDARD",Cycle.MONTHLY,UUID.randomUUID()));
    var pending = billing.checkout(jwt,o.id());
    var pool = Executors.newFixedThreadPool(2);
    var start = new CountDownLatch(1);
    try {
      Callable<Order> success = () -> { start.await(); return billing.mock(jwt,o.id(),new MockResult(pending.attemptId(),Outcome.PAID)); };
      Future<Order> first = pool.submit(success); Future<Order> second = pool.submit(success); start.countDown();
      assertEquals(OrderStatus.PAID,first.get(20,TimeUnit.SECONDS).status());
      assertEquals(OrderStatus.PAID,second.get(20,TimeUnit.SECONDS).status());
      assertEquals(1,payments.findAll().stream().filter(p -> p.getCustomerId().equals(customer.getId())).count());
      assertEquals(OrderStatus.PAID,attempts.findById(pending.attemptId()).orElseThrow().getStatus());
    } finally { pool.shutdownNow(); }
  }
}
