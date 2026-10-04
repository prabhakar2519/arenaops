package com.arena.core.repository;

import com.arena.core.entity.AccessOverrideEntity;
import com.arena.core.enums.AccessOverrideStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AccessOverrideRepository extends JpaRepository<AccessOverrideEntity, Long> {

  Optional<AccessOverrideEntity> findFirstByCustomerIdAndStatusAndEndsAtAfterOrderByEndsAtDesc(
      Long customerId, AccessOverrideStatus status, LocalDateTime now);

  List<AccessOverrideEntity> findByStatusAndEndsAtBefore(AccessOverrideStatus status, LocalDateTime now);

  List<AccessOverrideEntity> findByCustomerIdAndStatus(Long customerId, AccessOverrideStatus status);
}
