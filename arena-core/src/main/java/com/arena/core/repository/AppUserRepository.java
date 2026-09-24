package com.arena.core.repository;

import com.arena.core.entity.AppUserEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AppUserRepository extends JpaRepository<AppUserEntity, Long> {

  Optional<AppUserEntity> findByUsername(String username);

  boolean existsByUsername(String username);

  java.util.List<AppUserEntity> findByIdInAndRoleIgnoreCase(java.util.List<Long> ids, String role);

  java.util.List<AppUserEntity> findByCustomerIdAndIsActiveTrue(Long customerId);
}
