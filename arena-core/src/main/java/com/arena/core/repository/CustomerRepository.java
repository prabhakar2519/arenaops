package com.arena.core.repository;

import com.arena.core.entity.CustomerEntity;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerRepository extends JpaRepository<CustomerEntity, Long> {

  long countByStatus(String status);

  long countByAccessAllowedFalse();

  long countByNextDueDateBefore(LocalDate date);

  List<CustomerEntity> findTop8ByOrderByCreatedAtDesc();
}
