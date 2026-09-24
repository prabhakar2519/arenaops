package com.arena.core.repository;

import com.arena.core.entity.CustomerPaymentEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerPaymentRepository extends JpaRepository<CustomerPaymentEntity, Long> {

  List<CustomerPaymentEntity> findByCustomerIdOrderByCreatedAtDesc(Long customerId);
}
