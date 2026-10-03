package com.arena.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.arena.core.entity.AccessOverrideEntity;
import com.arena.core.entity.CustomerEntity;
import com.arena.core.entity.CustomerOnboardingCodeEntity;
import com.arena.core.entity.CustomerSubscriptionEntity;
import com.arena.core.enums.AccessOverrideStatus;
import com.arena.core.enums.AccessStatus;
import com.arena.core.enums.CustomerStatus;
import com.arena.core.enums.InvitationStatus;
import com.arena.core.enums.PaymentStatus;
import com.arena.core.enums.SubscriptionStatus;
import com.arena.core.model.AdminCustomerRequest;
import com.arena.core.model.AdminGracePeriodRequest;
import com.arena.core.model.AdminPaymentRequest;
import com.arena.core.repository.AccessOverrideRepository;
import com.arena.core.repository.CustomerOnboardingCodeRepository;
import com.arena.core.repository.CustomerPaymentRepository;
import com.arena.core.repository.CustomerRepository;
import com.arena.core.repository.CustomerSubscriptionRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AdminCustomerServiceTest {

  private CustomerRepository customerRepository;
  private CustomerOnboardingCodeRepository invitationRepository;
  private CustomerSubscriptionRepository subscriptionRepository;
  private CustomerPaymentRepository paymentRepository;
  private AccessOverrideRepository accessOverrideRepository;
  private ActivationCodeService activationCodeService;
  private AdminCustomerService service;
  private InvitationEmailService emailService;
  private AuditService auditService;

  @BeforeEach
  void setUp() {
    customerRepository = mock(CustomerRepository.class);
    invitationRepository = mock(CustomerOnboardingCodeRepository.class);
    subscriptionRepository = mock(CustomerSubscriptionRepository.class);
    paymentRepository = mock(CustomerPaymentRepository.class);
    accessOverrideRepository = mock(AccessOverrideRepository.class);
    activationCodeService = mock(ActivationCodeService.class);
    emailService = mock(InvitationEmailService.class);
    auditService = mock(AuditService.class);
    service = new AdminCustomerService(customerRepository, invitationRepository, subscriptionRepository,
        paymentRepository, accessOverrideRepository, activationCodeService, emailService, auditService,
        new com.arena.core.validation.CustomerEmailValidator(customerRepository, mock(com.arena.core.repository.AppUserRepository.class)));

    when(customerRepository.save(any(CustomerEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(customerRepository.saveAndFlush(any(CustomerEntity.class))).thenAnswer(invocation -> {
      CustomerEntity customer = invocation.getArgument(0);
      customer.setId(10L);
      return customer;
    });
    when(subscriptionRepository.save(any(CustomerSubscriptionEntity.class))).thenAnswer(invocation -> {
      CustomerSubscriptionEntity subscription = invocation.getArgument(0);
      if (subscription.getId() == null) subscription.setId(20L);
      return subscription;
    });
    when(invitationRepository.save(any(CustomerOnboardingCodeEntity.class))).thenAnswer(invocation -> {
      CustomerOnboardingCodeEntity invitation = invocation.getArgument(0);
      if (invitation.getId() == null) invitation.setId(30L);
      return invitation;
    });
    when(accessOverrideRepository.findFirstByCustomerIdAndStatusAndEndsAtAfterOrderByEndsAtDesc(any(), any(), any()))
        .thenReturn(Optional.empty());
    when(accessOverrideRepository.findByCustomerIdAndStatus(any(), any())).thenReturn(List.of());
    when(activationCodeService.generateCode()).thenReturn("ARENA-ABCDE-23456");
    when(activationCodeService.hashCode(anyString())).thenReturn("hashed-code");
    when(activationCodeService.normalize(anyString())).thenAnswer(invocation -> invocation.getArgument(0, String.class).trim().toUpperCase());
  }

  @Test
  void duplicateEmailIsRejectedBeforeCreatingCustomerOrInvitation() {
    when(customerRepository.existsByNormalizedEmail("owner@example.com")).thenReturn(true);
    assertThatThrownBy(() -> service.createCustomer(invitationRequest(), "arena_admin"))
        .isInstanceOf(com.arena.core.exception.ArenaOpsException.class)
        .hasMessageContaining("already exists with this email");
    org.mockito.Mockito.verify(customerRepository, org.mockito.Mockito.never()).saveAndFlush(any());
    org.mockito.Mockito.verify(invitationRepository, org.mockito.Mockito.never()).save(any());
    org.mockito.Mockito.verifyNoInteractions(emailService);
  }

  @Test
  void emailDisabledOrProviderFailurePreservesCreatedCustomerAndPreparedInvitation() {
    for (var code : List.of(com.arena.core.exception.ErrorCode.EMAIL_DISABLED,
        com.arena.core.exception.ErrorCode.EMAIL_DELIVERY_FAILED)) {
      org.mockito.Mockito.doThrow(new com.arena.core.exception.ArenaOpsException(code))
          .when(emailService).sendInvitation(anyString(), anyString(), anyString(), any());
      var response = service.createCustomer(invitationRequest(), "arena_admin");
      assertThat(response.getId()).isEqualTo(10L);
      assertThat(response.getCustomerStatus()).isEqualTo("INVITED");
      assertThat(response.getSubscriptionStatus()).isEqualTo("NOT_STARTED");
      assertThat(response.getInvitationStatus()).isEqualTo("CREATED");
      assertThat(response.getOnboardingCode()).isNotBlank();
      assertThat(response.getEmailDelivery().errorCode()).isEqualTo(code);
      assertThat(response.getEmailDelivery().status()).isEqualTo(code == com.arena.core.exception.ErrorCode.EMAIL_DISABLED
          ? com.arena.core.model.EmailDelivery.Status.DISABLED : com.arena.core.model.EmailDelivery.Status.FAILED);
    }
  }

  @Test
  void resendReportsActualDeliveryOutcomeAndRetainsReplacementCodeOnFailure() {
    CustomerEntity customer = invitedCustomer();
    CustomerOnboardingCodeEntity invitation = sentInvitation();
    when(customerRepository.findBillingCustomerForUpdate(10L)).thenReturn(Optional.of(customer));
    when(invitationRepository.findByCustomerId(10L)).thenReturn(List.of(invitation));
    when(subscriptionRepository.findFirstByCustomerIdOrderByCreatedAtDesc(10L)).thenReturn(Optional.of(notStartedSubscription()));
    var success = service.resendInvitation(10L, "arena_admin");
    assertThat(success.getEmailDelivery().status()).isEqualTo(com.arena.core.model.EmailDelivery.Status.SENT);
    for (var code : List.of(com.arena.core.exception.ErrorCode.EMAIL_DISABLED,
        com.arena.core.exception.ErrorCode.EMAIL_DELIVERY_FAILED)) {
      org.mockito.Mockito.doThrow(new com.arena.core.exception.ArenaOpsException(code))
          .when(emailService).sendInvitation(anyString(), anyString(), anyString(), any());
      var failed = service.resendInvitation(10L, "arena_admin");
      assertThat(failed.getInvitationStatus()).isEqualTo("CREATED");
      assertThat(failed.getEmailDelivery().errorCode()).isEqualTo(code);
      assertThat(failed.getOnboardingCode()).isNotBlank();
    }
    invitation.setInvitationStatus(InvitationStatus.CONSUMED);
    assertThatThrownBy(() -> service.resendInvitation(10L, "arena_admin"))
        .isInstanceOf(com.arena.core.exception.ArenaOpsException.class);
  }

  @Test
  void openingInvitationDoesNotConsumeCodeOrStartTrial() {
    var invitation = sentInvitation();
    var customer = invitedCustomer();
    var subscription = notStartedSubscription();
    when(invitationRepository.findByActivationCodeHashForUpdate("hashed-code")).thenReturn(Optional.of(invitation));
    when(customerRepository.findBillingCustomerForUpdate(10L)).thenReturn(Optional.of(customer));
    when(subscriptionRepository.findFirstByCustomerIdOrderByCreatedAtDesc(10L)).thenReturn(Optional.of(subscription));

    var response = service.validateInvitation("ARENA-ABCDE-23456", "owner@example.com");

    assertThat(response.getInvitedEmail()).isEqualTo("owner@example.com");
    assertThat(invitation.getInvitationStatus()).isEqualTo(InvitationStatus.SENT);
    assertThat(invitation.getUsedCount()).isZero();
    assertThat(invitation.getConsumedAt()).isNull();
    assertThat(customer.getCustomerStatus()).isEqualTo(CustomerStatus.INVITED);
    org.mockito.Mockito.verify(invitationRepository, org.mockito.Mockito.never()).save(any());
  }

  @Test
  void invitationValidationReturnsDistinctCodesForInvalidExpiredConsumedRevokedAndWrongEmail() {
    assertThatThrownBy(() -> service.validateInvitation("bad-code", "owner@example.com"))
        .isInstanceOfSatisfying(com.arena.core.exception.ArenaOpsException.class,
            error -> assertThat(error.getErrorCode()).isEqualTo(com.arena.core.exception.ErrorCode.INVITATION_INVALID));
    var invitation = sentInvitation();
    when(invitationRepository.findByActivationCodeHashForUpdate("hashed-code")).thenReturn(Optional.of(invitation));
    for (var status : List.of(InvitationStatus.EXPIRED, InvitationStatus.CONSUMED, InvitationStatus.REVOKED)) {
      invitation.setInvitationStatus(status);
      var expected = switch (status) {
        case EXPIRED -> com.arena.core.exception.ErrorCode.INVITATION_EXPIRED;
        case CONSUMED -> com.arena.core.exception.ErrorCode.INVITATION_ALREADY_CONSUMED;
        default -> com.arena.core.exception.ErrorCode.INVITATION_REVOKED;
      };
      assertThatThrownBy(() -> service.validateInvitation("ARENA-ABCDE-23456", "owner@example.com"))
          .isInstanceOfSatisfying(com.arena.core.exception.ArenaOpsException.class,
              error -> assertThat(error.getErrorCode()).isEqualTo(expected));
    }
    invitation.setInvitationStatus(InvitationStatus.SENT);
    assertThatThrownBy(() -> service.validateInvitation("ARENA-ABCDE-23456", "wrong@example.com"))
        .isInstanceOfSatisfying(com.arena.core.exception.ArenaOpsException.class,
            error -> assertThat(error.getErrorCode()).isEqualTo(com.arena.core.exception.ErrorCode.INVITATION_EMAIL_MISMATCH));
  }

  @Test
  void createsInvitationWithoutStartingTrial() {
    AdminCustomerRequest request = invitationRequest();

    var response = service.createCustomer(request, "arena_admin");

    assertThat(response.getCustomerStatus()).isEqualTo(CustomerStatus.INVITED.name());
    assertThat(response.getInvitationStatus()).isEqualTo(InvitationStatus.SENT.name());
    assertThat(response.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.NOT_STARTED.name());
    assertThat(response.getPaymentStatus()).isEqualTo(PaymentStatus.NOT_DUE.name());
    assertThat(response.getAccessStatus()).isEqualTo(AccessStatus.NOT_ALLOWED.name());
    assertThat(response.getOnboardingCode()).isEqualTo("ARENA-ABCDE-23456");
    assertThat(response.getEmailDelivery().status()).isEqualTo(com.arena.core.model.EmailDelivery.Status.SENT);
  }

  @Test
  void activationRequiresInvitedEmailAndConsumesCodeOnce() {
    CustomerEntity customer = invitedCustomer();
    CustomerSubscriptionEntity subscription = notStartedSubscription();
    CustomerOnboardingCodeEntity invitation = sentInvitation();
    when(customerRepository.findBillingCustomerForUpdate(10L)).thenReturn(Optional.of(customer));
    when(subscriptionRepository.findFirstByCustomerIdOrderByCreatedAtDesc(10L)).thenReturn(Optional.of(subscription));
    when(invitationRepository.findByActivationCodeHashForUpdate("hashed-code")).thenReturn(Optional.of(invitation));

    assertThatThrownBy(() -> service.activateCustomerFromInvitation("ARENA-ABCDE-23456", "wrong@example.com", "owner"))
        .hasMessageContaining("Registration email must match");

    CustomerEntity activated = service.activateCustomerFromInvitation("ARENA-ABCDE-23456", "owner@example.com", "owner");

    assertThat(activated.getCustomerStatus()).isEqualTo(CustomerStatus.ACTIVE);
    assertThat(activated.getAccessStatus()).isEqualTo(AccessStatus.ALLOWED);
    assertThat(subscription.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.TRIAL);
    assertThat(subscription.getTrialStartedAt()).isNotNull();
    assertThat(subscription.getTrialEndsAt()).isAfter(subscription.getTrialStartedAt());
    assertThat(invitation.getInvitationStatus()).isEqualTo(InvitationStatus.CONSUMED);
    assertThat(invitation.getUsedCount()).isEqualTo(1);
    assertThatThrownBy(() -> service.activateCustomerFromInvitation("ARENA-ABCDE-23456", "owner@example.com", "owner2"))
        .hasMessageContaining("already been used");
  }

  @Test
  void graceAllowsAccessWithoutChangingPaymentOrSubscriptionTruth() {
    CustomerEntity customer = activeCustomer(PaymentStatus.DUE, AccessStatus.BLOCKED);
    CustomerSubscriptionEntity subscription = activeSubscription(SubscriptionStatus.PAYMENT_DUE);
    when(customerRepository.findBillingCustomerForUpdate(10L)).thenReturn(Optional.of(customer));
    when(subscriptionRepository.findFirstByCustomerIdOrderByCreatedAtDesc(10L)).thenReturn(Optional.of(subscription));
    when(accessOverrideRepository.save(any(AccessOverrideEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    AdminGracePeriodRequest request = new AdminGracePeriodRequest();
    request.setEndsAt(LocalDateTime.now().plusDays(3));
    request.setReason("Waiting for bank transfer");

    var response = service.grantGracePeriod(10L, request, "arena_admin");

    assertThat(response.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.PAYMENT_DUE.name());
    assertThat(response.getPaymentStatus()).isEqualTo(PaymentStatus.DUE.name());
    assertThat(response.getAccessStatus()).isEqualTo(AccessStatus.ALLOWED.name());
  }

  @Test
  void paymentActivatesSubscriptionAndSuspensionOnlyBlocksAccess() {
    CustomerEntity customer = activeCustomer(PaymentStatus.DUE, AccessStatus.BLOCKED);
    CustomerSubscriptionEntity subscription = activeSubscription(SubscriptionStatus.PAYMENT_DUE);
    when(customerRepository.findBillingCustomerForUpdate(10L)).thenReturn(Optional.of(customer));
    when(subscriptionRepository.findFirstByCustomerIdOrderByCreatedAtDesc(10L)).thenReturn(Optional.of(subscription));
    AdminPaymentRequest payment = new AdminPaymentRequest();
    payment.setAmount(BigDecimal.valueOf(1200));

    var paid = service.recordPayment(10L, payment, "arena_admin");
    assertThat(paid.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.ACTIVE.name());
    assertThat(paid.getPaymentStatus()).isEqualTo(PaymentStatus.PAID.name());
    assertThat(paid.getAccessStatus()).isEqualTo(AccessStatus.ALLOWED.name());

    var suspended = service.suspendCustomer(10L, "arena_admin", "Policy review");
    assertThat(suspended.getCustomerStatus()).isEqualTo(CustomerStatus.SUSPENDED.name());
    assertThat(suspended.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.ACTIVE.name());
    assertThat(suspended.getPaymentStatus()).isEqualTo(PaymentStatus.PAID.name());
    assertThat(suspended.getAccessStatus()).isEqualTo(AccessStatus.BLOCKED.name());
  }

  @Test
  void cancelledCustomersCanReactivateAndPayWithoutRestartingTrial() {
    for (CustomerStatus status : List.of(CustomerStatus.INACTIVE, CustomerStatus.ACTIVE)) {
      CustomerEntity customer = activeCustomer(PaymentStatus.NOT_DUE, AccessStatus.BLOCKED);
      customer.setCustomerStatus(status);
      CustomerSubscriptionEntity subscription = activeSubscription(SubscriptionStatus.CANCELLED);
      LocalDateTime trialEnd = LocalDateTime.now().minusDays(2);
      subscription.setTrialStartedAt(trialEnd.minusDays(7));
      subscription.setTrialEndsAt(trialEnd);
      subscription.setCancelledAt(trialEnd.plusDays(1));
      subscription.setCancellationReason("TRIAL_NOT_CONVERTED");
      when(customerRepository.findBillingCustomerForUpdate(10L)).thenReturn(Optional.of(customer));
      when(subscriptionRepository.findFirstByCustomerIdOrderByCreatedAtDesc(10L)).thenReturn(Optional.of(subscription));

      var reopened = service.reactivateCustomer(10L, "arena_admin", "Customer returning");

      assertThat(reopened.getCustomerStatus()).isEqualTo("ACTIVE");
      assertThat(reopened.getSubscriptionStatus()).isEqualTo("PAYMENT_DUE");
      assertThat(reopened.getPaymentStatus()).isEqualTo("DUE");
      assertThat(reopened.getAccessStatus()).isEqualTo("BLOCKED");
      assertThat(customer.getAccessAllowed()).isFalse();
      assertThat(subscription.getTrialEndsAt()).isEqualTo(trialEnd);
      assertThat(subscription.getTrialStartedAt()).isEqualTo(trialEnd.minusDays(7));
      assertThat(subscription.getCancelledAt()).isEqualTo(trialEnd.plusDays(1));
      assertThat(subscription.getCancellationReason()).isEqualTo("TRIAL_NOT_CONVERTED");

      AdminPaymentRequest payment = new AdminPaymentRequest();
      payment.setAmount(BigDecimal.valueOf(3000));
      var paid = service.recordPayment(10L, payment, "arena_admin");
      assertThat(paid.getSubscriptionStatus()).isEqualTo("ACTIVE");
      assertThat(paid.getPaymentStatus()).isEqualTo("PAID");
      assertThat(paid.getAccessStatus()).isEqualTo("ALLOWED");
    }
  }

  @Test
  void cancelledCustomerCanReceiveGraceAfterReactivation() {
    CustomerEntity customer = activeCustomer(PaymentStatus.NOT_DUE, AccessStatus.BLOCKED);
    CustomerSubscriptionEntity subscription = activeSubscription(SubscriptionStatus.CANCELLED);
    when(customerRepository.findBillingCustomerForUpdate(10L)).thenReturn(Optional.of(customer));
    when(subscriptionRepository.findFirstByCustomerIdOrderByCreatedAtDesc(10L)).thenReturn(Optional.of(subscription));
    when(accessOverrideRepository.save(any(AccessOverrideEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

    service.reactivateCustomer(10L, "arena_admin", "Returning customer");
    AdminGracePeriodRequest grace = new AdminGracePeriodRequest();
    grace.setEndsAt(LocalDateTime.now().plusDays(3));
    var response = service.grantGracePeriod(10L, grace, "arena_admin");
    assertThat(response.getSubscriptionStatus()).isEqualTo("PAYMENT_DUE");
    assertThat(response.getPaymentStatus()).isEqualTo("DUE");
    assertThat(response.getAccessStatus()).isEqualTo("ALLOWED");
  }

  @Test
  void suspendedCustomerReactivationRequiresUnexpiredPaidPeriod() {
    CustomerEntity customer = activeCustomer(PaymentStatus.PAID, AccessStatus.BLOCKED);
    customer.setCustomerStatus(CustomerStatus.SUSPENDED);
    CustomerSubscriptionEntity subscription = activeSubscription(SubscriptionStatus.ACTIVE);
    subscription.setCurrentPeriodEnd(LocalDateTime.now().minusDays(1));
    when(customerRepository.findBillingCustomerForUpdate(10L)).thenReturn(Optional.of(customer));
    when(subscriptionRepository.findFirstByCustomerIdOrderByCreatedAtDesc(10L)).thenReturn(Optional.of(subscription));
    assertThat(service.reactivateCustomer(10L, "arena_admin", null).getAccessStatus()).isEqualTo("BLOCKED");

    customer.setCustomerStatus(CustomerStatus.SUSPENDED);
    subscription.setCurrentPeriodEnd(LocalDateTime.now().plusDays(10));
    assertThat(service.reactivateCustomer(10L, "arena_admin", null).getAccessStatus()).isEqualTo("ALLOWED");
  }

  @Test
  void invitedCustomerCannotBypassRegistrationThroughReactivation() {
    when(customerRepository.findBillingCustomerForUpdate(10L)).thenReturn(Optional.of(invitedCustomer()));
    when(subscriptionRepository.findFirstByCustomerIdOrderByCreatedAtDesc(10L)).thenReturn(Optional.of(notStartedSubscription()));
    assertThatThrownBy(() -> service.reactivateCustomer(10L, "arena_admin", null))
        .isInstanceOf(com.arena.core.exception.ArenaOpsException.class);
  }

  private AdminCustomerRequest invitationRequest() {
    AdminCustomerRequest request = new AdminCustomerRequest();
    request.setCustomerName("V Sports Academy");
    request.setOwnerName("Owner One");
    request.setOwnerEmail("Owner@Example.com");
    request.setOwnerPhone("9999999999");
    request.setSports(List.of("SINGLE"));
    request.setTrialDays(7);
    request.setInvitationExpiryDays(7);
    request.setSubscriptionType("MONTHLY");
    request.setBillingAmount(BigDecimal.valueOf(1000));
    return request;
  }

  private CustomerEntity invitedCustomer() {
    return CustomerEntity.builder()
        .id(10L)
        .customerName("V Sports Academy")
        .organizationName("V Sports Academy")
        .parlourName("V Sports Academy")
        .ownerName("Owner One")
        .ownerEmail("owner@example.com")
        .ownerPhone("9999999999")
        .sports("SINGLE")
        .subscriptionType("MONTHLY")
        .billingAmount(BigDecimal.valueOf(1000))
        .billingCurrency("INR")
        .paymentStatus(PaymentStatus.NOT_DUE.name())
        .status(CustomerStatus.INVITED.name())
        .customerStatus(CustomerStatus.INVITED)
        .accessAllowed(false)
        .accessStatus(AccessStatus.NOT_ALLOWED)
        .build();
  }

  private CustomerEntity activeCustomer(PaymentStatus paymentStatus, AccessStatus accessStatus) {
    CustomerEntity customer = invitedCustomer();
    customer.setCustomerStatus(CustomerStatus.ACTIVE);
    customer.setStatus(CustomerStatus.ACTIVE.name());
    customer.setPaymentStatus(paymentStatus.name());
    customer.setAccessStatus(accessStatus);
    customer.setAccessAllowed(accessStatus == AccessStatus.ALLOWED);
    return customer;
  }

  private CustomerSubscriptionEntity notStartedSubscription() {
    return CustomerSubscriptionEntity.builder()
        .id(20L)
        .customerId(10L)
        .plan("SINGLE")
        .subscriptionStatus(SubscriptionStatus.NOT_STARTED)
        .trialDurationDays(7)
        .build();
  }

  private CustomerSubscriptionEntity activeSubscription(SubscriptionStatus status) {
    CustomerSubscriptionEntity subscription = notStartedSubscription();
    subscription.setSubscriptionStatus(status);
    return subscription;
  }

  private CustomerOnboardingCodeEntity sentInvitation() {
    return CustomerOnboardingCodeEntity.builder()
        .id(30L)
        .customerId(10L)
        .code("hashed-code")
        .activationCodeHash("hashed-code")
        .invitedEmail("owner@example.com")
        .status(InvitationStatus.SENT.name())
        .invitationStatus(InvitationStatus.SENT)
        .maxUses(1)
        .usedCount(0)
        .expiresAt(LocalDateTime.now().plusDays(7))
        .build();
  }
}
