package com.arena.core.billing;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
import static com.arena.core.billing.BillingTypes.*;

/** Tenant-scoped order persistence and request deduplication. */
public interface BillingOrderRepository extends JpaRepository<BillingOrderEntity, UUID> {
  /** Resolves only an order owned by this customer. */
  Optional<BillingOrderEntity> findByIdAndCustomerId(UUID id, Long customerId);
  /** Replays an earlier creation request without creating another order. */
  Optional<BillingOrderEntity> findByCustomerIdAndRequestId(Long customerId, UUID requestId);
  /** Finds an unfinished checkout for safe resumption. */
  Optional<BillingOrderEntity> findFirstByCustomerIdAndStatusInOrderByCreatedAtDesc(Long customerId, Collection<OrderStatus> statuses);
}
