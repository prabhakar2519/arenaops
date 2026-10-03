package com.arena.core.repository;

import com.arena.core.entity.CustomerEntity;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.repository.query.Param;

@Repository
public interface CustomerRepository extends JpaRepository<CustomerEntity, Long> {

  @Query("select (count(c) > 0) from CustomerEntity c where lower(trim(c.ownerEmail)) = :email or lower(trim(c.primaryContactEmail)) = :email")
  boolean existsByNormalizedEmail(@Param("email") String email);

  /** Serializes customer billing writes so repeated callbacks cannot extend entitlement twice. */
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from CustomerEntity c where c.id = :id")
  java.util.Optional<CustomerEntity> findBillingCustomerForUpdate(@Param("id") Long id);

  long countByStatus(String status);

  long countByAccessAllowedFalse();

  long countByNextDueDateBefore(LocalDate date);

  List<CustomerEntity> findTop8ByOrderByCreatedAtDesc();
}
