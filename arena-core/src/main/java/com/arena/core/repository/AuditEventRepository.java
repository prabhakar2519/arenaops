package com.arena.core.repository;

import com.arena.core.entity.AuditEventEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditEventRepository extends JpaRepository<AuditEventEntity, Long> {

  List<AuditEventEntity> findByCustomerIdOrderByOccurredAtDesc(Long customerId);
}
