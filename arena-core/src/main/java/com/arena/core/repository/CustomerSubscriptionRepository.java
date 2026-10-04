package com.arena.core.repository;

import com.arena.core.entity.CustomerSubscriptionEntity;
import com.arena.core.enums.SubscriptionStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerSubscriptionRepository extends JpaRepository<CustomerSubscriptionEntity, Long> {

  Optional<CustomerSubscriptionEntity> findFirstByCustomerIdOrderByCreatedAtDesc(Long customerId);

  List<CustomerSubscriptionEntity> findBySubscriptionStatusAndTrialEndsAtBefore(
      SubscriptionStatus subscriptionStatus, LocalDateTime trialEndsAt);
}
