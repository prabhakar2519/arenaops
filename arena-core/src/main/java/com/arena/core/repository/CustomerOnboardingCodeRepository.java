package com.arena.core.repository;

import com.arena.core.entity.CustomerOnboardingCodeEntity;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerOnboardingCodeRepository extends JpaRepository<CustomerOnboardingCodeEntity, Long> {

  Optional<CustomerOnboardingCodeEntity> findByCode(String code);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select code from CustomerOnboardingCodeEntity code where code.code = :code")
  Optional<CustomerOnboardingCodeEntity> findByCodeForUpdate(@Param("code") String code);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select code from CustomerOnboardingCodeEntity code where code.activationCodeHash = :hash")
  Optional<CustomerOnboardingCodeEntity> findByActivationCodeHashForUpdate(@Param("hash") String hash);

  boolean existsByCode(String code);

  boolean existsByActivationCodeHash(String activationCodeHash);

  List<CustomerOnboardingCodeEntity> findByCustomerId(Long customerId);
}
