package com.arena.core.billing;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

/** Persists provider references and terminal results for idempotency. */
public interface BillingAttemptRepository extends JpaRepository<BillingAttemptEntity, UUID> {
  /** Returns the most recent payment attempt for display. */
  Optional<BillingAttemptEntity> findFirstByOrderIdOrderByCreatedAtDesc(UUID orderId);
  /** Checks the callback attempt belongs to its order. */
  Optional<BillingAttemptEntity> findByIdAndOrderId(UUID id, UUID orderId);
}
