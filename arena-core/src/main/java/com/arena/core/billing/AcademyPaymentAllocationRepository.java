package com.arena.core.billing;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AcademyPaymentAllocationRepository extends JpaRepository<AcademyPaymentAllocation, Long> {
  List<AcademyPaymentAllocation> findByAcademyIdAndChargeIdOrderByCreatedAt(Long academyId, Long chargeId);
  @Query("select coalesce(sum(allocation.amount), 0) from AcademyPaymentAllocation allocation where allocation.academyId = :academyId and allocation.chargeId = :chargeId")
  BigDecimal totalForCharge(@Param("academyId") Long academyId, @Param("chargeId") Long chargeId);
  @Query("select coalesce(sum(allocation.amount), 0) from AcademyPaymentAllocation allocation where allocation.academyId = :academyId")
  BigDecimal totalForAcademy(@Param("academyId") Long academyId);
}