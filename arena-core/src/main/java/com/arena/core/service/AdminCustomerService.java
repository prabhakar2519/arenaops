package com.arena.core.service;

import com.arena.core.entity.AccessOverrideEntity;
import com.arena.core.entity.CustomerEntity;
import com.arena.core.entity.CustomerOnboardingCodeEntity;
import com.arena.core.entity.CustomerPaymentEntity;
import com.arena.core.entity.CustomerSubscriptionEntity;
import com.arena.core.enums.AccessOverrideStatus;
import com.arena.core.enums.AccessOverrideType;
import com.arena.core.enums.AccessStatus;
import com.arena.core.enums.AuditActorType;
import com.arena.core.enums.CustomerStatus;
import com.arena.core.enums.InvitationStatus;
import com.arena.core.enums.PaymentStatus;
import com.arena.core.enums.SubscriptionStatus;
import com.arena.core.model.AdminCustomerRequest;
import com.arena.core.model.AdminCustomerResponse;
import com.arena.core.model.AdminDashboardResponse;
import com.arena.core.model.AdminGracePeriodRequest;
import com.arena.core.model.AdminPaymentRequest;
import com.arena.core.model.InvitationValidationResponse;
import com.arena.core.repository.AccessOverrideRepository;
import com.arena.core.repository.CustomerOnboardingCodeRepository;
import com.arena.core.repository.CustomerPaymentRepository;
import com.arena.core.repository.CustomerRepository;
import com.arena.core.repository.CustomerSubscriptionRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class AdminCustomerService {

  private static final List<String> CUSTOMER_PLANS = List.of("SINGLE", "DUAL", "MULTI_SPORTS", "ACADEMY");

  private final CustomerRepository customerRepository;
  private final CustomerOnboardingCodeRepository invitationRepository;
  private final CustomerSubscriptionRepository subscriptionRepository;
  private final CustomerPaymentRepository paymentRepository;
  private final AccessOverrideRepository accessOverrideRepository;
  private final ActivationCodeService activationCodeService;
  private final InvitationEmailService invitationEmailService;
  private final AuditService auditService;

  @Transactional
  public AdminCustomerResponse createCustomer(AdminCustomerRequest request, String adminUsername) {
    log.info("[Invitation] create requested; validating customer and creating invitation");
    LocalDateTime now = LocalDateTime.now();
    String organizationName = request.getCustomerName().trim();
    String contactEmail = normalizeEmail(request.getOwnerEmail());
    String plan = normalizePlan(request.getSports());
    int trialDays = Math.max(0, request.getTrialDays());
    int expiryDays = Math.max(1, request.getInvitationExpiryDays());
    String subscriptionType = normalizeSubscriptionType(defaultString(request.getSubscriptionType(), "MONTHLY"));
    BigDecimal billingAmount = request.getBillingAmount() == null ? BigDecimal.ZERO : request.getBillingAmount();
    String currency = defaultString(request.getBillingCurrency(), "INR").toUpperCase(Locale.ROOT);

    CustomerEntity customer = CustomerEntity.builder()
        .customerName(organizationName)
        .organizationName(organizationName)
        .organizationType(blankToNull(request.getOrganizationType()))
        .parlourName(blankToDefault(request.getParlourName(), organizationName))
        .ownerName(request.getOwnerName().trim())
        .primaryContactName(request.getOwnerName().trim())
        .ownerEmail(contactEmail)
        .primaryContactEmail(contactEmail)
        .ownerPhone(request.getOwnerPhone().trim())
        .primaryContactPhone(request.getOwnerPhone().trim())
        .sports(plan)
        .onboardingDate(now.toLocalDate())
        .trialStartsAt(now.toLocalDate())
        .trialEndsAt(now.toLocalDate())
        .subscriptionType(subscriptionType)
        .billingAmount(billingAmount)
        .billingCurrency(currency)
        .billingStartDate(now.toLocalDate())
        .nextDueDate(now.toLocalDate())
        .paymentStatus(PaymentStatus.NOT_DUE.name())
        .status(CustomerStatus.INVITED.name())
        .customerStatus(CustomerStatus.INVITED)
        .accessAllowed(false)
        .accessStatus(AccessStatus.NOT_ALLOWED)
        .notes(blankToNull(request.getNotes()))
        .createdBy(adminUsername)
        .build();

    CustomerEntity saved = customerRepository.saveAndFlush(customer);
    CustomerSubscriptionEntity subscription = subscriptionRepository.save(CustomerSubscriptionEntity.builder()
        .customerId(saved.getId())
        .plan(plan)
        .subscriptionStatus(SubscriptionStatus.NOT_STARTED)
        .trialDurationDays(trialDays)
        .build());

    String activationCode = uniqueActivationCode();
    String hash = activationCodeService.hashCode(activationCode);
    CustomerOnboardingCodeEntity invitation = invitationRepository.save(CustomerOnboardingCodeEntity.builder()
        .customerId(saved.getId())
        .code(hash)
        .activationCodeHash(hash)
        .invitedEmail(contactEmail)
        .status(InvitationStatus.CREATED.name())
        .invitationStatus(InvitationStatus.CREATED)
        .maxUses(1)
        .usedCount(0)
        .expiresAt(now.plusDays(expiryDays))
        .createdByAdminId(adminUsername)
        .build());

    audit("CUSTOMER_INVITATION_CREATED", saved, null, state(saved, subscription, invitation, activeGrace(saved.getId())),
        adminUsername, null);
    sendInvitation(saved, invitation, activationCode, adminUsername);
    return toResponse(saved, subscription, invitation, activeGrace(saved.getId()), activationCode);
  }

  @Transactional
  public AdminCustomerResponse resendInvitation(Long customerId, String adminUsername) {
    log.info("[Invitation] resend requested; customerId={}; checking invitation eligibility", customerId);
    CustomerEntity customer = customer(customerId);
    CustomerOnboardingCodeEntity invitation = latestInvitation(customerId);
    if (invitation == null) {
      throw new IllegalArgumentException("Customer has no invitation to resend");
    }
    if (invitationStatus(invitation) == InvitationStatus.CONSUMED) {
      throw new IllegalArgumentException("Consumed invitation cannot be resent");
    }
    if (invitationStatus(invitation) == InvitationStatus.REVOKED) {
      throw new IllegalArgumentException("Revoked invitation cannot be resent");
    }
    String activationCode = uniqueActivationCode();
    String hash = activationCodeService.hashCode(activationCode);
    invitation.setCode(hash);
    invitation.setActivationCodeHash(hash);
    invitation.setStatus(InvitationStatus.CREATED.name());
    invitation.setInvitationStatus(InvitationStatus.CREATED);
    invitation.setExpiresAt(LocalDateTime.now().plusDays(7));
    invitation.setUsedCount(0);
    invitation.setUsedAt(null);
    invitation.setUsedByUsername(null);
    CustomerOnboardingCodeEntity saved = invitationRepository.save(invitation);
    sendInvitation(customer, saved, activationCode, adminUsername);
    audit("INVITATION_RESENT", customer, null, state(customer, latestSubscription(customerId).orElse(null), saved,
        activeGrace(customerId)), adminUsername, null);
    return toResponse(customer, latestSubscription(customerId).orElse(null), saved, activeGrace(customerId), activationCode);
  }

  @Transactional
  public AdminCustomerResponse revokeInvitation(Long invitationId, String adminUsername, String reason) {
    CustomerOnboardingCodeEntity invitation = invitationRepository.findById(invitationId)
        .orElseThrow(() -> new IllegalArgumentException("Invitation not found: " + invitationId));
    if (invitationStatus(invitation) == InvitationStatus.CONSUMED) {
      throw new IllegalArgumentException("Consumed invitation cannot be revoked");
    }
    CustomerEntity customer = customer(invitation.getCustomerId());
    CustomerSubscriptionEntity subscription = latestSubscription(customer.getId()).orElse(null);
    String previous = state(customer, subscription, invitation, activeGrace(customer.getId()));
    invitation.setStatus(InvitationStatus.REVOKED.name());
    invitation.setInvitationStatus(InvitationStatus.REVOKED);
    invitation.setRevokedAt(LocalDateTime.now());
    invitation.setRevokedByAdminId(adminUsername);
    invitation.setRevocationReason(blankToNull(reason));
    CustomerOnboardingCodeEntity saved = invitationRepository.save(invitation);
    audit("INVITATION_REVOKED", customer, previous, state(customer, subscription, saved, activeGrace(customer.getId())),
        adminUsername, reason);
    return toResponse(customer, subscription, saved, activeGrace(customer.getId()), null);
  }

  @Transactional
  public InvitationValidationResponse validateInvitation(String activationCode, String email) {
    CustomerOnboardingCodeEntity invitation = findInvitationForActivation(activationCode);
    validateInvitationCanBeConsumed(invitation, email);
    CustomerEntity customer = customer(invitation.getCustomerId());
    CustomerSubscriptionEntity subscription = latestSubscription(customer.getId()).orElse(null);
    return InvitationValidationResponse.builder()
        .customerId(customer.getId())
        .organizationName(resolveOrganizationName(customer))
        .invitedEmail(invitation.getInvitedEmail())
        .plan(subscription != null ? subscription.getPlan() : customer.getSports())
        .trialDurationDays(subscription != null ? subscription.getTrialDurationDays() : 0)
        .invitationStatus(invitationStatus(invitation).name())
        .build();
  }

  @Transactional
  public CustomerEntity activateCustomerFromInvitation(String activationCode, String email, String username) {
    CustomerOnboardingCodeEntity invitation = findInvitationForActivation(activationCode);
    validateInvitationCanBeConsumed(invitation, email);
    CustomerEntity customer = customer(invitation.getCustomerId());
    CustomerSubscriptionEntity subscription = requireSubscription(customer.getId());
    if (customer.getCustomerStatus() == CustomerStatus.ACTIVE) {
      throw new IllegalArgumentException("Customer is already active");
    }

    LocalDateTime now = LocalDateTime.now();
    LocalDateTime trialEndsAt = now.plusDays(subscription.getTrialDurationDays());
    String previous = state(customer, subscription, invitation, activeGrace(customer.getId()));

    invitation.setUsedCount(invitation.getUsedCount() + 1);
    invitation.setUsedAt(now);
    invitation.setConsumedAt(now);
    invitation.setUsedByUsername(username);
    invitation.setStatus(InvitationStatus.CONSUMED.name());
    invitation.setInvitationStatus(InvitationStatus.CONSUMED);

    customer.setCustomerStatus(CustomerStatus.ACTIVE);
    customer.setStatus(CustomerStatus.ACTIVE.name());
    customer.setActivatedAt(now);
    customer.setAccessStatus(AccessStatus.ALLOWED);
    customer.setAccessAllowed(true);
    customer.setPaymentStatus(PaymentStatus.NOT_DUE.name());
    customer.setTrialStartsAt(now.toLocalDate());
    customer.setTrialEndsAt(trialEndsAt.toLocalDate());
    customer.setBillingStartDate(trialEndsAt.toLocalDate());
    customer.setNextDueDate(trialEndsAt.toLocalDate());

    subscription.setSubscriptionStatus(SubscriptionStatus.TRIAL);
    subscription.setTrialStartedAt(now);
    subscription.setTrialEndsAt(trialEndsAt);
    subscription.setNextBillingDate(trialEndsAt);

    invitationRepository.save(invitation);
    subscriptionRepository.save(subscription);
    CustomerEntity saved = customerRepository.save(customer);
    audit("CUSTOMER_REGISTERED", saved, previous, state(saved, subscription, invitation, activeGrace(saved.getId())),
        AuditActorType.CUSTOMER, username, null, null);
    audit("TRIAL_STARTED", saved, null, state(saved, subscription, invitation, activeGrace(saved.getId())),
        AuditActorType.SYSTEM, "system", null, null);
    return saved;
  }

  @Transactional(readOnly = true)
  public List<AdminCustomerResponse> listCustomers() {
    return customerRepository.findAll().stream()
        .map(customer -> toResponse(customer, latestSubscription(customer.getId()).orElse(null), latestInvitation(customer.getId()),
            activeGrace(customer.getId()), null))
        .collect(Collectors.toList());
  }

  @Transactional(readOnly = true)
  public AdminDashboardResponse getDashboard() {
    List<AdminCustomerResponse> customers = listCustomers();
    BigDecimal monthlyRecurringRevenue = customers.stream()
        .filter(customer -> "MONTHLY".equalsIgnoreCase(customer.getSubscriptionType()))
        .filter(customer -> SubscriptionStatus.ACTIVE.name().equals(customer.getSubscriptionStatus()))
        .map(AdminCustomerResponse::getBillingAmount)
        .filter(Objects::nonNull)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    return AdminDashboardResponse.builder()
        .totalCustomers(customers.size())
        .activeCustomers(customers.stream().filter(customer -> CustomerStatus.ACTIVE.name().equals(customer.getCustomerStatus())).count())
        .pendingRegistration(customers.stream().filter(customer -> CustomerStatus.INVITED.name().equals(customer.getCustomerStatus())).count())
        .accessBlocked(customers.stream().filter(customer -> AccessStatus.BLOCKED.name().equals(customer.getAccessStatus())).count())
        .paymentDue(customers.stream().filter(customer -> PaymentStatus.DUE.name().equals(customer.getPaymentStatus())).count())
        .monthlyRecurringRevenue(monthlyRecurringRevenue)
        .yearlyRecurringRevenue(BigDecimal.ZERO)
        .recentCustomers(customers.stream().limit(8).toList())
        .build();
  }

  @Transactional
  public AdminCustomerResponse grantGracePeriod(Long customerId, AdminGracePeriodRequest request, String adminUsername) {
    CustomerEntity customer = customer(customerId);
    CustomerSubscriptionEntity subscription = requireSubscription(customerId);
    expireLifecycleForCustomer(customer, subscription);
    if (subscription.getSubscriptionStatus() != SubscriptionStatus.PAYMENT_DUE) {
      throw new IllegalArgumentException("Grace period can only be granted when payment is due");
    }
    if (request.getEndsAt() == null || !request.getEndsAt().isAfter(LocalDateTime.now())) {
      throw new IllegalArgumentException("Grace period end must be in the future");
    }
    if (!accessOverrideRepository.findByCustomerIdAndStatus(customerId, AccessOverrideStatus.ACTIVE).isEmpty()) {
      throw new IllegalArgumentException("Customer already has an active grace period");
    }
    String previous = state(customer, subscription, latestInvitation(customerId), activeGrace(customerId));
    AccessOverrideEntity grace = accessOverrideRepository.save(AccessOverrideEntity.builder()
        .customerId(customerId)
        .subscriptionId(subscription.getId())
        .overrideType(AccessOverrideType.GRACE_PERIOD)
        .status(AccessOverrideStatus.ACTIVE)
        .startsAt(LocalDateTime.now())
        .endsAt(request.getEndsAt())
        .reason(blankToNull(request.getReason()))
        .createdBy(adminUsername)
        .build());
    customer.setAccessStatus(AccessStatus.ALLOWED);
    customer.setAccessAllowed(true);
    CustomerEntity saved = customerRepository.save(customer);
    audit("GRACE_GRANTED", saved, previous, state(saved, subscription, latestInvitation(customerId), Optional.of(grace)),
        adminUsername, request.getReason());
    return toResponse(saved, subscription, latestInvitation(customerId), Optional.of(grace), null);
  }

  @Transactional
  public AdminCustomerResponse recordPayment(Long customerId, AdminPaymentRequest request, String adminUsername) {
    CustomerEntity customer = customer(customerId);
    CustomerSubscriptionEntity subscription = requireSubscription(customerId);
    if (customer.getCustomerStatus() == CustomerStatus.INACTIVE || subscription.getSubscriptionStatus() == SubscriptionStatus.CANCELLED) {
      throw new IllegalArgumentException("Cancelled customers must be reactivated before recording payment");
    }
    String previous = state(customer, subscription, latestInvitation(customerId), activeGrace(customerId));
    LocalDateTime paidAt = request.getPaymentDate() != null ? request.getPaymentDate() : LocalDateTime.now();
    paymentRepository.save(CustomerPaymentEntity.builder()
        .customerId(customerId)
        .subscriptionId(subscription.getId())
        .amount(request.getAmount())
        .currency(defaultString(request.getCurrency(), customer.getBillingCurrency()).toUpperCase(Locale.ROOT))
        .paymentStatus(PaymentStatus.PAID)
        .paymentMethod(blankToNull(request.getPaymentMethod()))
        .paymentReference(blankToNull(request.getPaymentReference()))
        .paymentDate(paidAt)
        .recordedBy(adminUsername)
        .notes(blankToNull(request.getNotes()))
        .build());

    LocalDateTime periodEnd = "YEARLY".equalsIgnoreCase(customer.getSubscriptionType()) ? paidAt.plusYears(1) : paidAt.plusMonths(1);
    subscription.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
    subscription.setSubscriptionStartedAt(subscription.getSubscriptionStartedAt() == null ? paidAt : subscription.getSubscriptionStartedAt());
    subscription.setCurrentPeriodStart(paidAt);
    subscription.setCurrentPeriodEnd(periodEnd);
    subscription.setNextBillingDate(periodEnd);
    customer.setCustomerStatus(CustomerStatus.ACTIVE);
    customer.setStatus(CustomerStatus.ACTIVE.name());
    customer.setAccessStatus(AccessStatus.ALLOWED);
    customer.setAccessAllowed(true);
    customer.setPaymentStatus(PaymentStatus.PAID.name());
    customer.setPaymentActivatedBy(adminUsername);
    customer.setPaymentActivatedAt(paidAt);
    customer.setNextDueDate(periodEnd.toLocalDate());
    revokeActiveGrace(customerId, adminUsername, "Payment received");

    CustomerEntity saved = customerRepository.save(customer);
    CustomerSubscriptionEntity savedSubscription = subscriptionRepository.save(subscription);
    audit("PAYMENT_RECORDED", saved, previous, state(saved, savedSubscription, latestInvitation(customerId), activeGrace(customerId)),
        adminUsername, request.getNotes());
    audit("SUBSCRIPTION_ACTIVATED", saved, null, state(saved, savedSubscription, latestInvitation(customerId), activeGrace(customerId)),
        AuditActorType.SYSTEM, "system", null, null);
    return toResponse(saved, savedSubscription, latestInvitation(customerId), activeGrace(customerId), null);
  }

  @Transactional
  public AdminCustomerResponse cancelSubscription(Long customerId, String adminUsername, String reason) {
    CustomerEntity customer = customer(customerId);
    CustomerSubscriptionEntity subscription = requireSubscription(customerId);
    String previous = state(customer, subscription, latestInvitation(customerId), activeGrace(customerId));
    subscription.setSubscriptionStatus(SubscriptionStatus.CANCELLED);
    subscription.setCancelledAt(LocalDateTime.now());
    subscription.setCancellationReason(blankToDefault(reason, "TRIAL_NOT_CONVERTED"));
    customer.setCustomerStatus(CustomerStatus.INACTIVE);
    customer.setStatus(CustomerStatus.INACTIVE.name());
    customer.setAccessStatus(AccessStatus.BLOCKED);
    customer.setAccessAllowed(false);
    customer.setPaymentStatus(PaymentStatus.NOT_DUE.name());
    customer.setDeactivatedAt(LocalDateTime.now());
    customer.setDeactivationReason(blankToDefault(reason, "TRIAL_NOT_CONVERTED"));
    revokeActiveGrace(customerId, adminUsername, "Subscription cancelled");
    CustomerEntity saved = customerRepository.save(customer);
    CustomerSubscriptionEntity savedSubscription = subscriptionRepository.save(subscription);
    audit("SUBSCRIPTION_CANCELLED", saved, previous, state(saved, savedSubscription, latestInvitation(customerId), activeGrace(customerId)),
        adminUsername, reason);
    return toResponse(saved, savedSubscription, latestInvitation(customerId), activeGrace(customerId), null);
  }

  @Transactional
  public AdminCustomerResponse suspendCustomer(Long customerId, String adminUsername, String reason) {
    CustomerEntity customer = customer(customerId);
    CustomerSubscriptionEntity subscription = requireSubscription(customerId);
    String previous = state(customer, subscription, latestInvitation(customerId), activeGrace(customerId));
    customer.setCustomerStatus(CustomerStatus.SUSPENDED);
    customer.setStatus(CustomerStatus.SUSPENDED.name());
    customer.setAccessStatus(AccessStatus.BLOCKED);
    customer.setAccessAllowed(false);
    customer.setSuspendedBy(adminUsername);
    customer.setSuspendedAt(LocalDateTime.now());
    customer.setSuspensionReason(blankToNull(reason));
    CustomerEntity saved = customerRepository.save(customer);
    audit("CUSTOMER_SUSPENDED", saved, previous, state(saved, subscription, latestInvitation(customerId), activeGrace(customerId)),
        adminUsername, reason);
    return toResponse(saved, subscription, latestInvitation(customerId), activeGrace(customerId), null);
  }

  @Transactional
  public AdminCustomerResponse reactivateCustomer(Long customerId, String adminUsername, String reason) {
    CustomerEntity customer = customer(customerId);
    CustomerSubscriptionEntity subscription = requireSubscription(customerId);
    String previous = state(customer, subscription, latestInvitation(customerId), activeGrace(customerId));
    customer.setCustomerStatus(CustomerStatus.ACTIVE);
    customer.setStatus(CustomerStatus.ACTIVE.name());
    customer.setDeactivatedAt(null);
    customer.setDeactivationReason(null);
    customer.setSuspendedAt(null);
    customer.setSuspendedBy(null);
    customer.setSuspensionReason(null);
    applyAccessFromSubscription(customer, subscription);
    CustomerEntity saved = customerRepository.save(customer);
    audit("CUSTOMER_REACTIVATED", saved, previous, state(saved, subscription, latestInvitation(customerId), activeGrace(customerId)),
        adminUsername, reason);
    return toResponse(saved, subscription, latestInvitation(customerId), activeGrace(customerId), null);
  }

  @Transactional
  public void expireTrialsAndGracePeriods() {
    LocalDateTime now = LocalDateTime.now();
    subscriptionRepository.findBySubscriptionStatusAndTrialEndsAtBefore(SubscriptionStatus.TRIAL, now)
        .forEach(subscription -> expireTrial(subscription));
    accessOverrideRepository.findByStatusAndEndsAtBefore(AccessOverrideStatus.ACTIVE, now)
        .forEach(this::expireGrace);
  }

  @Transactional
  public void expireLifecycleForCustomer(CustomerEntity customer, CustomerSubscriptionEntity subscription) {
    LocalDateTime now = LocalDateTime.now();
    if (subscription.getSubscriptionStatus() == SubscriptionStatus.TRIAL
        && subscription.getTrialEndsAt() != null
        && subscription.getTrialEndsAt().isBefore(now)) {
      expireTrial(subscription);
    }
    activeGrace(customer.getId()).ifPresent(grace -> {
      if (grace.getEndsAt().isBefore(now)) {
        expireGrace(grace);
      }
    });
  }

  private void expireTrial(CustomerSubscriptionEntity subscription) {
    CustomerEntity customer = customer(subscription.getCustomerId());
    if (customer.getCustomerStatus() == CustomerStatus.INACTIVE || customer.getCustomerStatus() == CustomerStatus.SUSPENDED) {
      return;
    }
    String previous = state(customer, subscription, latestInvitation(customer.getId()), activeGrace(customer.getId()));
    subscription.setSubscriptionStatus(SubscriptionStatus.PAYMENT_DUE);
    customer.setPaymentStatus(PaymentStatus.DUE.name());
    if (activeGrace(customer.getId()).isPresent()) {
      customer.setAccessStatus(AccessStatus.ALLOWED);
      customer.setAccessAllowed(true);
    } else {
      customer.setAccessStatus(AccessStatus.BLOCKED);
      customer.setAccessAllowed(false);
    }
    subscriptionRepository.save(subscription);
    CustomerEntity saved = customerRepository.save(customer);
    audit("TRIAL_EXPIRED", saved, previous, state(saved, subscription, latestInvitation(saved.getId()), activeGrace(saved.getId())),
        AuditActorType.SYSTEM, "system", null, null);
    audit("PAYMENT_BECAME_DUE", saved, null, state(saved, subscription, latestInvitation(saved.getId()), activeGrace(saved.getId())),
        AuditActorType.SYSTEM, "system", null, null);
  }

  private void expireGrace(AccessOverrideEntity grace) {
    CustomerEntity customer = customer(grace.getCustomerId());
    CustomerSubscriptionEntity subscription = requireSubscription(customer.getId());
    String previous = state(customer, subscription, latestInvitation(customer.getId()), Optional.of(grace));
    grace.setStatus(AccessOverrideStatus.EXPIRED);
    accessOverrideRepository.save(grace);
    if (subscription.getSubscriptionStatus() == SubscriptionStatus.PAYMENT_DUE
        && customer.getCustomerStatus() == CustomerStatus.ACTIVE) {
      customer.setAccessStatus(AccessStatus.BLOCKED);
      customer.setAccessAllowed(false);
      customerRepository.save(customer);
    }
    audit("GRACE_EXPIRED", customer, previous, state(customer, subscription, latestInvitation(customer.getId()), Optional.of(grace)),
        AuditActorType.SYSTEM, "system", null, null);
  }

  private void applyAccessFromSubscription(CustomerEntity customer, CustomerSubscriptionEntity subscription) {
    if (customer.getCustomerStatus() == CustomerStatus.SUSPENDED || customer.getCustomerStatus() == CustomerStatus.INACTIVE) {
      customer.setAccessStatus(AccessStatus.BLOCKED);
      customer.setAccessAllowed(false);
      return;
    }
    if (subscription.getSubscriptionStatus() == SubscriptionStatus.TRIAL
        || subscription.getSubscriptionStatus() == SubscriptionStatus.ACTIVE
        || activeGrace(customer.getId()).isPresent()) {
      customer.setAccessStatus(AccessStatus.ALLOWED);
      customer.setAccessAllowed(true);
      return;
    }
    customer.setAccessStatus(AccessStatus.BLOCKED);
    customer.setAccessAllowed(false);
  }

  private void sendInvitation(CustomerEntity customer, CustomerOnboardingCodeEntity invitation, String activationCode,
      String adminUsername) {
    String previous = state(customer, latestSubscription(customer.getId()).orElse(null), invitation, activeGrace(customer.getId()));
    log.info("[Invitation] sending; customerId={} invitationId={} transport={}", customer.getId(), invitation.getId(), invitationEmailService.getClass().getSimpleName());
    try {
      invitationEmailService.sendInvitation(resolveOrganizationName(customer), invitation.getInvitedEmail(),
          activationCode, invitation.getExpiresAt());
      log.info("[Invitation] transport accepted; customerId={} invitationId={}; recording SENT status and audit event", customer.getId(), invitation.getId());
      invitation.setStatus(InvitationStatus.SENT.name());
      invitation.setInvitationStatus(InvitationStatus.SENT);
      invitation.setSentAt(LocalDateTime.now());
      invitationRepository.save(invitation);
      audit("INVITATION_EMAIL_SENT", customer, previous,
          state(customer, latestSubscription(customer.getId()).orElse(null), invitation, activeGrace(customer.getId())),
          adminUsername, null);
    } catch (RuntimeException ex) {
      log.error("[Invitation] failed; customerId={} invitationId={} errorType={}; recording failure audit; see Email logs for SMTP details", customer.getId(), invitation.getId(), ex.getClass().getSimpleName());
      audit("INVITATION_EMAIL_FAILED", customer, previous, previous, adminUsername, ex.getMessage());
    }
  }

  private CustomerOnboardingCodeEntity findInvitationForActivation(String activationCode) {
    String normalized = activationCodeService.normalize(activationCode);
    if (normalized.isBlank()) {
      throw new IllegalArgumentException("Activation code is required");
    }
    String hash = activationCodeService.hashCode(normalized);
    return invitationRepository.findByActivationCodeHashForUpdate(hash)
        .or(() -> invitationRepository.findByCodeForUpdate(normalized))
        .orElseThrow(() -> new IllegalArgumentException("Invalid activation code"));
  }

  private void validateInvitationCanBeConsumed(CustomerOnboardingCodeEntity invitation, String email) {
    InvitationStatus status = invitationStatus(invitation);
    if (status == InvitationStatus.EXPIRED || invitation.getExpiresAt().isBefore(LocalDateTime.now())) {
      invitation.setStatus(InvitationStatus.EXPIRED.name());
      invitation.setInvitationStatus(InvitationStatus.EXPIRED);
      invitationRepository.save(invitation);
      throw new IllegalArgumentException("Activation code has expired");
    }
    if (status == InvitationStatus.REVOKED) {
      throw new IllegalArgumentException("Activation code has been revoked");
    }
    if (status == InvitationStatus.CONSUMED || invitation.getUsedCount() >= invitation.getMaxUses()) {
      throw new IllegalArgumentException("Activation code has already been used");
    }
    if (status != InvitationStatus.SENT && status != InvitationStatus.CREATED) {
      throw new IllegalArgumentException("Activation code is not ready for registration");
    }
    if (!normalizeEmail(email).equals(normalizeEmail(invitation.getInvitedEmail()))) {
      throw new IllegalArgumentException("Registration email must match the invited email");
    }
  }

  private String uniqueActivationCode() {
    String code;
    String hash;
    do {
      code = activationCodeService.generateCode();
      hash = activationCodeService.hashCode(code);
    } while (invitationRepository.existsByActivationCodeHash(hash) || invitationRepository.existsByCode(hash));
    return code;
  }

  private AdminCustomerResponse toResponse(CustomerEntity customer, CustomerSubscriptionEntity subscription,
      CustomerOnboardingCodeEntity invitation, Optional<AccessOverrideEntity> grace, String activationCode) {
    AccessOverrideEntity activeGrace = grace.orElse(null);
    return AdminCustomerResponse.builder()
        .id(customer.getId())
        .organizationName(resolveOrganizationName(customer))
        .organizationType(customer.getOrganizationType())
        .primaryContactName(defaultString(customer.getPrimaryContactName(), customer.getOwnerName()))
        .primaryContactEmail(defaultString(customer.getPrimaryContactEmail(), customer.getOwnerEmail()))
        .primaryContactPhone(defaultString(customer.getPrimaryContactPhone(), customer.getOwnerPhone()))
        .customerName(customer.getCustomerName())
        .parlourName(customer.getParlourName())
        .ownerName(customer.getOwnerName())
        .ownerEmail(customer.getOwnerEmail())
        .ownerPhone(customer.getOwnerPhone())
        .sports(splitSports(subscription != null ? subscription.getPlan() : customer.getSports()))
        .onboardingDate(customer.getOnboardingDate())
        .trialStartsAt(customer.getTrialStartsAt())
        .trialEndsAt(customer.getTrialEndsAt())
        .subscriptionType(customer.getSubscriptionType())
        .billingAmount(customer.getBillingAmount())
        .billingCurrency(customer.getBillingCurrency())
        .billingStartDate(customer.getBillingStartDate())
        .nextDueDate(customer.getNextDueDate())
        .trialDurationDays(subscription != null ? subscription.getTrialDurationDays() : null)
        .trialStartedAt(subscription != null ? subscription.getTrialStartedAt() : null)
        .trialEndsAtDateTime(subscription != null ? subscription.getTrialEndsAt() : null)
        .nextBillingDate(subscription != null ? subscription.getNextBillingDate() : null)
        .subscriptionId(subscription != null ? subscription.getId() : null)
        .customerStatus(customer.getCustomerStatus() != null ? customer.getCustomerStatus().name() : customer.getStatus())
        .invitationStatus(invitation != null ? invitationStatus(invitation).name() : null)
        .subscriptionStatus(subscription != null ? subscription.getSubscriptionStatus().name() : null)
        .paymentStatus(customer.getPaymentStatus())
        .accessStatus(customer.getAccessStatus() != null ? customer.getAccessStatus().name() : null)
        .graceStatus(activeGrace != null ? activeGrace.getStatus().name() : null)
        .graceEndsAt(activeGrace != null ? activeGrace.getEndsAt() : null)
        .status(customer.getStatus())
        .accessAllowed(customer.getAccessAllowed())
        .trialExpired(subscription != null && subscription.getTrialEndsAt() != null
            && LocalDateTime.now().isAfter(subscription.getTrialEndsAt()))
        .usable(customer.getAccessStatus() == AccessStatus.ALLOWED)
        .notes(customer.getNotes())
        .onboardingCode(activationCode)
        .invitationId(invitation != null ? invitation.getId() : null)
        .onboardingCodeStatus(invitation != null ? invitationStatus(invitation).name() : null)
        .onboardingCodeExpiresAt(invitation != null ? invitation.getExpiresAt() : null)
        .paymentActivatedBy(customer.getPaymentActivatedBy())
        .paymentActivatedAt(customer.getPaymentActivatedAt())
        .accessUpdatedBy(customer.getAccessUpdatedBy())
        .accessUpdatedAt(customer.getAccessUpdatedAt())
        .createdAt(customer.getCreatedAt())
        .updatedAt(customer.getUpdatedAt())
        .build();
  }

  private CustomerEntity customer(Long customerId) {
    return customerRepository.findById(customerId)
        .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + customerId));
  }

  private CustomerSubscriptionEntity requireSubscription(Long customerId) {
    return latestSubscription(customerId)
        .orElseThrow(() -> new IllegalStateException("Subscription record is missing for customer " + customerId));
  }

  private Optional<CustomerSubscriptionEntity> latestSubscription(Long customerId) {
    return subscriptionRepository.findFirstByCustomerIdOrderByCreatedAtDesc(customerId);
  }

  private CustomerOnboardingCodeEntity latestInvitation(Long customerId) {
    return invitationRepository.findByCustomerId(customerId).stream()
        .max((left, right) -> left.getCreatedAt().compareTo(right.getCreatedAt()))
        .orElse(null);
  }

  private Optional<AccessOverrideEntity> activeGrace(Long customerId) {
    return accessOverrideRepository.findFirstByCustomerIdAndStatusAndEndsAtAfterOrderByEndsAtDesc(
        customerId, AccessOverrideStatus.ACTIVE, LocalDateTime.now());
  }

  private void revokeActiveGrace(Long customerId, String adminUsername, String reason) {
    accessOverrideRepository.findByCustomerIdAndStatus(customerId, AccessOverrideStatus.ACTIVE)
        .forEach(grace -> {
          grace.setStatus(AccessOverrideStatus.REVOKED);
          grace.setRevokedAt(LocalDateTime.now());
          grace.setRevokedBy(adminUsername);
          grace.setRevocationReason(reason);
          accessOverrideRepository.save(grace);
        });
  }

  private InvitationStatus invitationStatus(CustomerOnboardingCodeEntity invitation) {
    if (invitation.getInvitationStatus() != null) {
      return invitation.getInvitationStatus();
    }
    return InvitationStatus.valueOf(invitation.getStatus().toUpperCase(Locale.ROOT));
  }

  private String normalizePlan(List<String> sports) {
    String value = sports == null ? "" : sports.stream()
        .filter(sport -> sport != null && !sport.isBlank())
        .map(sport -> sport.trim().toUpperCase(Locale.ROOT))
        .distinct()
        .collect(Collectors.joining(","));
    if (value.isBlank()) {
      throw new IllegalArgumentException("Plan is required");
    }
    if (!CUSTOMER_PLANS.contains(value)) {
      throw new IllegalArgumentException("Plan must be SINGLE, DUAL, MULTI_SPORTS, or ACADEMY");
    }
    return value;
  }

  private String normalizeSubscriptionType(String subscriptionType) {
    String value = subscriptionType.trim().toUpperCase(Locale.ROOT);
    if (!value.equals("MONTHLY") && !value.equals("YEARLY")) {
      throw new IllegalArgumentException("Subscription type must be MONTHLY or YEARLY");
    }
    return value;
  }

  private List<String> splitSports(String sports) {
    if (sports == null || sports.isBlank()) {
      return List.of();
    }
    return Arrays.stream(sports.split(",")).map(String::trim).filter(value -> !value.isBlank()).toList();
  }

  private String normalizeEmail(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Email is required");
    }
    return value.trim().toLowerCase(Locale.ROOT);
  }

  private String resolveOrganizationName(CustomerEntity customer) {
    return defaultString(customer.getOrganizationName(), customer.getCustomerName());
  }

  private String state(CustomerEntity customer, CustomerSubscriptionEntity subscription,
      CustomerOnboardingCodeEntity invitation, Optional<AccessOverrideEntity> grace) {
    return state(customer, subscription, invitation, grace.orElse(null));
  }

  private String state(CustomerEntity customer, CustomerSubscriptionEntity subscription,
      CustomerOnboardingCodeEntity invitation, AccessOverrideEntity grace) {
    return "customer=" + (customer.getCustomerStatus() != null ? customer.getCustomerStatus() : customer.getStatus())
        + ", subscription=" + (subscription != null ? subscription.getSubscriptionStatus() : null)
        + ", payment=" + customer.getPaymentStatus()
        + ", access=" + customer.getAccessStatus()
        + ", invitation=" + (invitation != null ? invitationStatus(invitation) : null)
        + ", grace=" + (grace != null ? grace.getStatus() : null);
  }

  private void audit(String eventType, CustomerEntity customer, String previousState, String newState,
      String adminUsername, String reason) {
    audit(eventType, customer, previousState, newState, AuditActorType.ADMIN, adminUsername, reason, null);
  }

  private void audit(String eventType, CustomerEntity customer, String previousState, String newState,
      AuditActorType actorType, String actorId, String reason, String metadata) {
    auditService.record(eventType, customer.getId(), actorType, actorId, previousState, newState, reason, metadata);
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private String blankToDefault(String value, String defaultValue) {
    return value == null || value.isBlank() ? defaultValue : value.trim();
  }

  private String defaultString(String value, String defaultValue) {
    return value == null || value.isBlank() ? defaultValue : value.trim();
  }
}
