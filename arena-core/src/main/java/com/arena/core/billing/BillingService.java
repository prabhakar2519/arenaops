package com.arena.core.billing;

import com.arena.core.entity.*;
import com.arena.core.enums.*;
import com.arena.core.exception.*;
import com.arena.core.repository.*;
import com.arena.core.service.AdminAuthorizationService;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.arena.core.billing.BillingTypes.*;

/** Owns tenant-scoped checkout and atomically applies verified payment results to existing entitlements. */
@Service @RequiredArgsConstructor @Slf4j
@Transactional
public class BillingService {
  private final jakarta.persistence.EntityManager entityManager;
  private final AppUserRepository users;
  private final CustomerRepository customers;
  private final CustomerSubscriptionRepository subscriptions;
  private final CustomerPaymentRepository payments;
  private final BillingOrderRepository orders;
  private final BillingAttemptRepository attempts;
  private final BillingPricing pricing;
  private final PaymentGateway gateway;
  private final AdminAuthorizationService authorization;

  /** Resolves ownership from the authenticated identity, never from browser tenant identifiers. */
  private CustomerEntity owner(Jwt jwt) {
    AppUserEntity user = users.findByUsername(authorization.resolveUsername(jwt)).orElse(null);
    if (user == null || !"OWNER".equalsIgnoreCase(user.getRole()) || !Boolean.TRUE.equals(user.getIsActive())
        || user.getCustomerId() == null) {
      log.warn("Unauthorized billing access");
      throw new ArenaOpsException(ErrorCode.ACCESS_DENIED);
    }
    // All billing writes for one customer serialize, including duplicate callbacks and separate renewal orders.
    CustomerEntity customer = customers.findBillingCustomerForUpdate(user.getCustomerId())
        .orElseThrow(() -> new ArenaOpsException(ErrorCode.ACCESS_DENIED));
    if (customer.getCustomerStatus() != CustomerStatus.ACTIVE)
      throw new ArenaOpsException(ErrorCode.CUSTOMER_ACCESS_BLOCKED);
    return customer;
  }

  /** Reuses the existing subscription, including its sports entitlement. */
  private CustomerSubscriptionEntity subscription(CustomerEntity customer) {
    return subscriptions.findFirstByCustomerIdOrderByCreatedAtDesc(customer.getId())
        .orElseThrow(() -> new ArenaOpsException(ErrorCode.SUBSCRIPTION_NOT_FOUND));
  }

  /** Produces current effective dates and any resumable checkout from persisted state. */
  public Overview overview(Jwt jwt) {
    CustomerEntity c = owner(jwt);
    CustomerSubscriptionEntity s = subscription(c);
    LocalDateTime now = LocalDateTime.now();
    SubscriptionStatus status = s.getSubscriptionStatus();
    if (status == SubscriptionStatus.TRIAL && s.getTrialEndsAt() != null && !s.getTrialEndsAt().isAfter(now))
      status = SubscriptionStatus.PAYMENT_DUE;
    if (status == SubscriptionStatus.ACTIVE && s.getCurrentPeriodEnd() != null && !s.getCurrentPeriodEnd().isAfter(now))
      status = SubscriptionStatus.EXPIRED;
    long days = s.getTrialEndsAt() == null ? 0 : Math.max(0, (ChronoUnit.SECONDS.between(now, s.getTrialEndsAt()) + 86399) / 86400);
    var pending = orders.findFirstByCustomerIdAndStatusInOrderByCreatedAtDesc(c.getId(),
        List.of(OrderStatus.CREATED, OrderStatus.PAYMENT_PENDING, OrderStatus.FAILED));
    return new Overview(c.getParlourName(), status.name(), s.getBillingPlanId() == null ? (status == SubscriptionStatus.TRIAL ? "Trial" : s.getPlan()) : "ArenaOps Standard", s.getPlan(), c.getBillingAmount(), c.getBillingCurrency(), c.getSubscriptionType(), c.getPaymentStatus(),
        s.getTrialStartedAt(), s.getTrialEndsAt(), days, s.getSubscriptionStartedAt(), s.getCurrentPeriodEnd(),
        gateway.mockEnabled(), pricing.plans(), pending.filter(o -> o.getExpiresAt().isAfter(now)).map(o -> view(o,c)).orElse(null));
  }

  /** Snapshots server pricing and safely replays duplicate creation requests. */
  public Order create(Jwt jwt, CreateOrder request) {
    CustomerEntity c = owner(jwt);
    var plan = pricing.price(request.planId(), request.cycle());
    CustomerSubscriptionEntity s = subscription(c);
    if (s.getSubscriptionStatus() == SubscriptionStatus.CANCELLED || s.getSubscriptionStatus() == SubscriptionStatus.NOT_STARTED)
      throw new ArenaOpsException(ErrorCode.INVALID_STATE);
    var replay = orders.findByCustomerIdAndRequestId(c.getId(), request.requestId());
    if (replay.isPresent()) {
      if (!replay.get().getPlanId().equals(request.planId()) || replay.get().getCycle() != request.cycle())
        throw new ArenaOpsException(ErrorCode.CONFLICT);
      return view(replay.get(), c);
    }
    var pending = orders.findFirstByCustomerIdAndStatusInOrderByCreatedAtDesc(c.getId(),
        List.of(OrderStatus.CREATED, OrderStatus.PAYMENT_PENDING));
    if (pending.isPresent() && pending.get().getExpiresAt().isAfter(LocalDateTime.now()))
      throw new ArenaOpsException(ErrorCode.CONFLICT);
    BillingOrderEntity o = new BillingOrderEntity();
    o.setId(UUID.randomUUID()); o.setCustomerId(c.getId()); o.setSubscriptionId(s.getId());
    o.setRequestId(request.requestId()); o.setPlanId(plan.id()); o.setPlanName(plan.name()); o.setCycle(plan.cycle());
    o.setBaseAmount(plan.baseAmount()); o.setTaxAmount(plan.taxAmount()); o.setTotal(plan.total()); o.setCurrency(plan.currency());
    o.setStatus(OrderStatus.CREATED); o.setCreatedAt(LocalDateTime.now()); o.setUpdatedAt(o.getCreatedAt());
    o.setExpiresAt(o.getCreatedAt().plusHours(24)); orders.save(o);
    log.info("Billing order created customerId={} orderId={} planId={} cycle={}", c.getId(), o.getId(), o.getPlanId(), o.getCycle());
    return view(o,c);
  }

  /** Retrieves an order only within the authenticated customer's tenant. */
  public Order get(Jwt jwt, UUID id) { CustomerEntity c = owner(jwt); return view(ownedOrder(id,c),c); }

  /** Starts a new attempt after failure, or resumes the existing pending attempt. */
  public Order checkout(Jwt jwt, UUID id) {
    CustomerEntity c = owner(jwt); BillingOrderEntity o = ownedOrder(id,c);
    validForPayment(o,c);
    if (o.getStatus() == OrderStatus.PAYMENT_PENDING) return view(o,c);
    if (o.getStatus() != OrderStatus.CREATED && o.getStatus() != OrderStatus.FAILED) invalid(o);
    UUID attemptId = UUID.randomUUID();
    PaymentGateway.Session session = gateway.initiate(attemptId, o.getTotal(), o.getCurrency());
    BillingAttemptEntity a = new BillingAttemptEntity();
    a.setId(attemptId); a.setOrderId(id); a.setAmount(o.getTotal()); a.setCurrency(o.getCurrency());
    a.setProvider(session.provider()); a.setProviderReference(session.reference());
    a.setStatus(OrderStatus.PAYMENT_PENDING); a.setCreatedAt(LocalDateTime.now()); attempts.save(a);
    o.setStatus(OrderStatus.PAYMENT_PENDING); o.setUpdatedAt(LocalDateTime.now()); orders.save(o);
    log.info("Payment initiated customerId={} orderId={} paymentId={}", c.getId(), id, attemptId);
    return view(o,c);
  }

  /** Abandons an order before a provider attempt; provider checkout cancellation uses its verified result. */
  public Order cancel(Jwt jwt, UUID id) {
    CustomerEntity c = owner(jwt); BillingOrderEntity o = ownedOrder(id,c);
    if (o.getStatus() == OrderStatus.CANCELLED) return view(o,c);
    if (o.getStatus() != OrderStatus.CREATED && o.getStatus() != OrderStatus.FAILED) invalid(o);
    o.setStatus(OrderStatus.CANCELLED); o.setUpdatedAt(LocalDateTime.now()); orders.save(o);
    log.info("Billing order cancelled customerId={} orderId={}", c.getId(), id);
    return view(o,c);
  }

  /** Guards mock verification before passing a provider-confirmed result to the domain transition. */
  public Order mock(Jwt jwt, UUID id, MockResult result) {
    Outcome verified = gateway.verifyMock(result.outcome());
    CustomerEntity c = owner(jwt); BillingOrderEntity o = ownedOrder(id,c);
    BillingAttemptEntity a = attempts.findByIdAndOrderId(result.attemptId(), id)
        .orElseThrow(() -> new ArenaOpsException(ErrorCode.RESOURCE_NOT_FOUND));
    applyVerifiedResult(c,o,a,verified);
    return view(o,c);
  }

  /** Applies a server-verified provider callback; this method must never be exposed directly as a browser API. */
  public void processVerifiedPayment(PaymentGateway.VerifiedPayment result) {
    BillingAttemptEntity a = attempts.findById(result.attemptId())
        .orElseThrow(() -> new ArenaOpsException(ErrorCode.RESOURCE_NOT_FOUND));
    BillingOrderEntity snapshot = orders.findById(a.getOrderId())
        .orElseThrow(() -> new ArenaOpsException(ErrorCode.RESOURCE_NOT_FOUND));
    CustomerEntity c = customers.findBillingCustomerForUpdate(snapshot.getCustomerId())
        .orElseThrow(() -> new ArenaOpsException(ErrorCode.RESOURCE_NOT_FOUND));
    if (c.getCustomerStatus() != CustomerStatus.ACTIVE) throw new ArenaOpsException(ErrorCode.CUSTOMER_ACCESS_BLOCKED);
    // Refresh after acquiring the customer lock: another callback may have completed while we waited.
    entityManager.refresh(a);
    entityManager.refresh(snapshot);
    if (result.outcome() == null || !a.getProviderReference().equals(result.providerReference())
        || result.amount() == null || a.getAmount().compareTo(result.amount()) != 0
        || !a.getCurrency().equals(result.currency())) invalid(snapshot);
    applyVerifiedResult(c,snapshot,a,result.outcome());
  }

  /** Applies only a verified adapter result; future webhook adapters must call this inside the same customer lock. */
  private void applyVerifiedResult(CustomerEntity c, BillingOrderEntity o, BillingAttemptEntity a, Outcome result) {
    OrderStatus target = OrderStatus.valueOf(result.name());
    if (a.getStatus() != OrderStatus.PAYMENT_PENDING) {
      if (a.getStatus() != target) invalid(o);
      log.info("Duplicate payment ignored customerId={} orderId={} paymentId={}", c.getId(), o.getId(), a.getId());
      return;
    }
    validForPayment(o,c);
    if (o.getStatus() != OrderStatus.PAYMENT_PENDING || a.getAmount().compareTo(o.getTotal()) != 0
        || !a.getCurrency().equals(o.getCurrency())) invalid(o);
    a.setStatus(target); a.setCompletedAt(LocalDateTime.now()); attempts.save(a);
    o.setStatus(target); o.setUpdatedAt(LocalDateTime.now()); orders.save(o);
    if (result == Outcome.PAID) activate(c,o,a);
    log.info("Payment processed customerId={} orderId={} paymentId={} outcome={}", c.getId(), o.getId(), a.getId(), result);
  }

  /** Activates once in the result transaction, extending an existing unexpired paid term without losing days. */
  private void activate(CustomerEntity c, BillingOrderEntity o, BillingAttemptEntity a) {
    CustomerSubscriptionEntity s = subscription(c); LocalDateTime now = LocalDateTime.now();
    LocalDateTime start = s.getSubscriptionStatus() == SubscriptionStatus.ACTIVE && s.getCurrentPeriodEnd() != null
        && s.getCurrentPeriodEnd().isAfter(now) ? s.getCurrentPeriodEnd() : now;
    LocalDateTime end = o.getCycle() == Cycle.ANNUAL ? start.plusYears(1) : start.plusMonths(1);
    s.setBillingPlanId(o.getPlanId());
    s.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
    if (s.getSubscriptionStartedAt() == null) s.setSubscriptionStartedAt(now);
    if (s.getCurrentPeriodStart() == null || !start.isAfter(now)) s.setCurrentPeriodStart(start);
    s.setCurrentPeriodEnd(end); s.setNextBillingDate(end); subscriptions.save(s);
    payments.save(CustomerPaymentEntity.builder().customerId(c.getId()).subscriptionId(s.getId())
        .amount(o.getTotal()).currency(o.getCurrency()).paymentStatus(PaymentStatus.PAID)
        .paymentMethod(a.getProvider()).paymentReference(a.getProviderReference()).paymentDate(now)
        .recordedBy("BILLING_GATEWAY").build());
    c.setSubscriptionType(o.getCycle().name()); c.setBillingAmount(o.getTotal()); c.setBillingCurrency(o.getCurrency());
    c.setPaymentStatus(PaymentStatus.PAID.name()); c.setBillingStartDate(start.toLocalDate()); c.setNextDueDate(end.toLocalDate());
    c.setAccessAllowed(true); c.setAccessStatus(AccessStatus.ALLOWED); c.setPaymentActivatedAt(now);
    c.setPaymentActivatedBy("BILLING_GATEWAY"); customers.save(c);
    log.info("Subscription activated customerId={} orderId={} subscriptionId={}", c.getId(), o.getId(), s.getId());
  }

  /** Hides foreign order existence behind the same missing-resource response. */
  private BillingOrderEntity ownedOrder(UUID id, CustomerEntity c) {
    return orders.findByIdAndCustomerId(id,c.getId()).orElseThrow(() -> {
      log.warn("Billing order unavailable customerId={} orderId={}", c.getId(), id);
      return new ArenaOpsException(ErrorCode.RESOURCE_NOT_FOUND);
    });
  }
  /** Rejects expired orders and entitlements cancelled since creation. */
  private void validForPayment(BillingOrderEntity o, CustomerEntity c) {
    var s = subscription(c);
    if (!o.getExpiresAt().isAfter(LocalDateTime.now()) || !o.getSubscriptionId().equals(s.getId())
        || s.getSubscriptionStatus() == SubscriptionStatus.CANCELLED || s.getSubscriptionStatus() == SubscriptionStatus.NOT_STARTED) invalid(o);
  }
  /** Records invalid state transitions without exposing internal details. */
  private void invalid(BillingOrderEntity o) {
    log.warn("Invalid billing transition customerId={} orderId={} status={}", o.getCustomerId(), o.getId(), o.getStatus());
    throw new ArenaOpsException(ErrorCode.INVALID_STATE);
  }
  /** Maps the persisted monetary snapshot and latest attempt to a safe response. */
  private Order view(BillingOrderEntity o, CustomerEntity c) {
    var a = attempts.findFirstByOrderIdOrderByCreatedAtDesc(o.getId()).orElse(null);
    return new Order(o.getId(), c.getParlourName(), o.getPlanId(), o.getPlanName(), o.getCycle(), o.getBaseAmount(),
        o.getTaxAmount(), o.getTotal(), o.getCurrency(), o.getStatus(), o.getExpiresAt(),
        a == null ? null : a.getId(), a == null ? null : a.getStatus().name());
  }
}
