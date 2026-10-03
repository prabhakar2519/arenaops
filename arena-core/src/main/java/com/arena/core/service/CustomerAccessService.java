package com.arena.core.service;

import com.arena.core.exception.ArenaOpsException;
import com.arena.core.exception.ErrorCode;
import com.arena.core.entity.AppUserEntity;
import com.arena.core.entity.AccessOverrideEntity;
import com.arena.core.entity.CustomerEntity;
import com.arena.core.entity.CustomerSubscriptionEntity;
import com.arena.core.enums.AccessOverrideStatus;
import com.arena.core.enums.AccessStatus;
import com.arena.core.enums.CustomerStatus;
import com.arena.core.enums.PaymentStatus;
import com.arena.core.enums.SubscriptionStatus;
import com.arena.core.repository.AccessOverrideRepository;
import com.arena.core.repository.CustomerRepository;
import com.arena.core.repository.CustomerSubscriptionRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomerAccessService {

  private final CustomerRepository customerRepository;
  private final CustomerSubscriptionRepository subscriptionRepository;
  private final AccessOverrideRepository accessOverrideRepository;

  public void assertCustomerCanUseApp(AppUserEntity user) {
    if (user == null || "ADMIN".equalsIgnoreCase(user.getRole())) {
      return;
    }

    if (Boolean.FALSE.equals(user.getIsActive())) {
      throw new ArenaOpsException(ErrorCode.USER_DISABLED);
    }

    if (user.getCustomerId() == null) {
      return;
    }

    CustomerEntity customer = customerRepository.findById(user.getCustomerId())
        .orElseThrow(() -> new ArenaOpsException(ErrorCode.CUSTOMER_ACCESS_BLOCKED));
    CustomerSubscriptionEntity subscription = subscriptionRepository.findFirstByCustomerIdOrderByCreatedAtDesc(customer.getId())
        .orElse(null);
    evaluateLifecycle(customer, subscription);

    if (customer.getAccessStatus() == AccessStatus.NOT_ALLOWED) {
      throw new ArenaOpsException(ErrorCode.CUSTOMER_ACCESS_BLOCKED);
    }

    if (customer.getAccessStatus() == AccessStatus.BLOCKED || Boolean.FALSE.equals(customer.getAccessAllowed())) {
      if (customer.getCustomerStatus() == CustomerStatus.ACTIVE && subscription != null) {
        if (subscription.getSubscriptionStatus() == SubscriptionStatus.PAYMENT_DUE) {
          throw new ArenaOpsException(ErrorCode.PAYMENT_REQUIRED);
        }
        if (subscription.getSubscriptionStatus() == SubscriptionStatus.CANCELLED) {
          throw new ArenaOpsException(ErrorCode.SUBSCRIPTION_ACCESS_BLOCKED);
        }
      }
      throw new ArenaOpsException(ErrorCode.CUSTOMER_ACCESS_BLOCKED);
    }

    if (!hasCurrentEntitlement(customer, subscription)) {
      throw new ArenaOpsException(PaymentStatus.PAID.name().equalsIgnoreCase(customer.getPaymentStatus())
          ? ErrorCode.SUBSCRIPTION_EXPIRED : ErrorCode.PAYMENT_REQUIRED);
    }

    if (customer.getCustomerStatus() == CustomerStatus.SUSPENDED || customer.getCustomerStatus() == CustomerStatus.INACTIVE) {
      throw new ArenaOpsException(ErrorCode.CUSTOMER_ACCESS_BLOCKED);
    }
  }

  public boolean isCustomerUsable(CustomerEntity customer) {
    if (customer == null || customer.getAccessStatus() != AccessStatus.ALLOWED || Boolean.FALSE.equals(customer.getAccessAllowed())) {
      return false;
    }
    if (customer.getCustomerStatus() == CustomerStatus.SUSPENDED || customer.getCustomerStatus() == CustomerStatus.INACTIVE) {
      return false;
    }
    CustomerSubscriptionEntity subscription = subscriptionRepository.findFirstByCustomerIdOrderByCreatedAtDesc(customer.getId())
        .orElse(null);
    return hasCurrentEntitlement(customer, subscription);
  }

  public boolean hasCurrentEntitlement(CustomerEntity customer, CustomerSubscriptionEntity subscription) {
    if (customer == null || subscription == null) {
      return false;
    }

    LocalDateTime now = LocalDateTime.now();
    if (subscription.getSubscriptionStatus() == SubscriptionStatus.ACTIVE) {
      return subscription.getCurrentPeriodEnd() == null || !now.isAfter(subscription.getCurrentPeriodEnd());
    }

    if (subscription.getSubscriptionStatus() == SubscriptionStatus.TRIAL) {
      return subscription.getTrialEndsAt() != null && !now.isAfter(subscription.getTrialEndsAt());
    }

    return hasActiveGrace(customer.getId(), now);
  }

  private void evaluateLifecycle(CustomerEntity customer, CustomerSubscriptionEntity subscription) {
    if (customer == null || subscription == null) {
      return;
    }
    LocalDateTime now = LocalDateTime.now();
    accessOverrideRepository.findByStatusAndEndsAtBefore(AccessOverrideStatus.ACTIVE, now)
        .forEach(grace -> {
          grace.setStatus(AccessOverrideStatus.EXPIRED);
          accessOverrideRepository.save(grace);
        });

    if (subscription.getSubscriptionStatus() == SubscriptionStatus.TRIAL
        && subscription.getTrialEndsAt() != null
        && now.isAfter(subscription.getTrialEndsAt())) {
      subscription.setSubscriptionStatus(SubscriptionStatus.PAYMENT_DUE);
      subscriptionRepository.save(subscription);
      customer.setPaymentStatus(PaymentStatus.DUE.name());
    }

    if (customer.getCustomerStatus() == CustomerStatus.SUSPENDED || customer.getCustomerStatus() == CustomerStatus.INACTIVE) {
      customer.setAccessStatus(AccessStatus.BLOCKED);
      customer.setAccessAllowed(false);
    } else if (subscription.getSubscriptionStatus() == SubscriptionStatus.TRIAL
        || subscription.getSubscriptionStatus() == SubscriptionStatus.ACTIVE
        || hasActiveGrace(customer.getId(), now)) {
      customer.setAccessStatus(AccessStatus.ALLOWED);
      customer.setAccessAllowed(true);
    } else if (customer.getCustomerStatus() == CustomerStatus.INVITED) {
      customer.setAccessStatus(AccessStatus.NOT_ALLOWED);
      customer.setAccessAllowed(false);
    } else {
      customer.setAccessStatus(AccessStatus.BLOCKED);
      customer.setAccessAllowed(false);
    }
    customerRepository.save(customer);
  }

  private boolean hasActiveGrace(Long customerId, LocalDateTime now) {
    return accessOverrideRepository.findFirstByCustomerIdAndStatusAndEndsAtAfterOrderByEndsAtDesc(
        customerId, AccessOverrideStatus.ACTIVE, now).isPresent();
  }
}
