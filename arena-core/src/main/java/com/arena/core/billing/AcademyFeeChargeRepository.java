package com.arena.core.billing;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AcademyFeeChargeRepository extends JpaRepository<AcademyFeeCharge, Long> {
  List<AcademyFeeCharge> findByAcademyIdOrderByDueDateAsc(Long academyId);
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select charge from AcademyFeeCharge charge where charge.id = :id and charge.academyId = :academyId")
  Optional<AcademyFeeCharge> findByIdAndAcademyIdForUpdate(@Param("id") Long id, @Param("academyId") Long academyId);
}