package com.arena.core.repository;

import com.arena.core.entity.AppUserEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.repository.query.Param;

@Repository
public interface AppUserRepository extends JpaRepository<AppUserEntity, Long> {

  Optional<AppUserEntity> findByUsername(String username);

  boolean existsByUsername(String username);

  @Query("select (count(u) > 0) from AppUserEntity u where lower(trim(u.email)) = :email")
  boolean existsByNormalizedEmail(@Param("email") String email);

  java.util.List<AppUserEntity> findByIdInAndRoleIgnoreCase(java.util.List<Long> ids, String role);

  java.util.List<AppUserEntity> findByCustomerIdAndIsActiveTrue(Long customerId);
}
