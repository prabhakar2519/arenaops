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

  @BeforeEach
  void setUp() {
    customerRepository = mock(CustomerRepository.class);
    invitationRepository = mock(CustomerOnboardingCodeRepository.class);
    subscriptionRepository = mock(CustomerSubscriptionRepository.class);
    paymentRepository = mock(CustomerPaymentRepository.class);
    accessOverrideRepository = mock(AccessOverrideRepository.class);
    activationCodeService = mock(ActivationCodeService.class);
    InvitationEmailService emailService = (organizationName, invitedEmail, activationCode, expiresAt) -> { };
    AuditService auditService = mock(AuditService.class);
    service = new AdminCustomerService(customerRepository, invitationRepository, subscriptionRepository,
        paymentRepository, accessOverrideRepository, activationCodeService, emailService, auditService);

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
  void createsInvitationWithoutStartingTrial() {
    AdminCustomerRequest request = invitationRequest();

    var response = service.createCustomer(request, "arena_admin");

    assertThat(response.getCustomerStatus()).isEqualTo(CustomerStatus.INVITED.name());
    assertThat(response.getInvitationStatus()).isEqualTo(InvitationStatus.SENT.name());
    assertThat(response.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.NOT_STARTED.name());
    assertThat(response.getPaymentStatus()).isEqualTo(PaymentStatus.NOT_DUE.name());
    assertThat(response.getAccessStatus()).isEqualTo(AccessStatus.NOT_ALLOWED.name());
    assertThat(response.getOnboardingCode()).isEqualTo("ARENA-ABCDE-23456");
  }

  @Test
  void activationRequiresInvitedEmailAndConsumesCodeOnce() {
    CustomerEntity customer = invitedCustomer();
    CustomerSubscriptionEntity subscription = notStartedSubscription();
    CustomerOnboardingCodeEntity invitation = sentInvitation();
    when(customerRepository.findById(10L)).thenReturn(Optional.of(customer));
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
    when(customerRepository.findById(10L)).thenReturn(Optional.of(customer));
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
    when(customerRepository.findById(10L)).thenReturn(Optional.of(customer));
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
