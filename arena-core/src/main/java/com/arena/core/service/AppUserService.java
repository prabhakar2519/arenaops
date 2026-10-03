package com.arena.core.service;

import com.arena.core.exception.ArenaOpsException;
import com.arena.core.exception.ErrorCode;
import com.arena.core.entity.AppUserEntity;
import com.arena.core.entity.CustomerEntity;
import com.arena.core.model.AppUserRequest;
import com.arena.core.model.AppUserResponse;
import com.arena.core.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppUserService {

  private final AppUserRepository appUserRepository;
  private final PasswordEncoder passwordEncoder;
  private final KeycloakService keycloakService;
  private final AdminCustomerService adminCustomerService;

  @Transactional
  public AppUserResponse save(AppUserRequest request) {
    if (!"OWNER".equalsIgnoreCase(request.getRole())) {
      throw new ArenaOpsException(ErrorCode.ACCESS_DENIED);
    }
    request.setRole("OWNER");
    request.setIsActive(true);
    log.info("[AppUserService] save() - username={}, email={}, role={}", request.getUsername(), request.getEmail(),
        request.getRole());

    log.info("[AppUserService] Checking if user '{}' exists in DB...", request.getUsername());
    if ("OWNER".equalsIgnoreCase(request.getRole())) {
      validateOwnerRegistrationRequest(request);
    }

    boolean existsInDb = appUserRepository.existsByUsername(request.getUsername());
    log.info("[AppUserService] User '{}' existsInDb={}", request.getUsername(), existsInDb);

    if (existsInDb) {
      log.warn("[AppUserService] Duplicate user rejected: {}", request.getUsername());
      throw new ArenaOpsException(ErrorCode.USER_ALREADY_EXISTS);
    }

    log.info("[AppUserService] Checking if user '{}' exists in Keycloak...", request.getUsername());
    boolean existsInKeycloak = keycloakService.existsInKeycloak(request.getUsername());
    log.info("[AppUserService] User '{}' existsInKeycloak={}", request.getUsername(), existsInKeycloak);
    if (existsInKeycloak) {
      throw new ArenaOpsException(ErrorCode.USER_ALREADY_EXISTS);
    }

    String passwordHash = passwordEncoder.encode(request.getPassword());

    CustomerEntity customer = null;
    if ("OWNER".equalsIgnoreCase(request.getRole())) {
      customer = adminCustomerService.activateCustomerFromInvitation(
          request.getOnboardingCode(), request.getEmail(), request.getUsername());
    }

    log.info("[AppUserService] Saving user '{}' to DB...", request.getUsername());
    AppUserEntity entity = AppUserEntity.builder()
        .username(request.getUsername())
        .email(request.getEmail())
        .displayName(request.getName())
        .passwordHash(passwordHash)
        .role(request.getRole())
        .customerId(customer != null ? customer.getId() : null)
        .isActive(request.getIsActive() != null ? request.getIsActive() : true)
        .build();

    AppUserEntity saved = appUserRepository.saveAndFlush(entity);
    log.info("[AppUserService] User '{}' saved in DB with id={}", saved.getUsername(), saved.getId());

    log.info("[AppUserService] Creating user '{}' in Keycloak...", request.getUsername());
    try {
      String keycloakId = keycloakService.createUser(
          request.getUsername(), request.getPassword(), request.getEmail(), request.getRole());
      log.info("[AppUserService] User '{}' created in Keycloak with id={}", request.getUsername(), keycloakId);
    } catch (ArenaOpsException e) {
      throw e;
    } catch (Exception e) {
      log.warn("Identity creation failed userId={} type={}", saved.getId(), e.getClass().getSimpleName());
      throw new ArenaOpsException(ErrorCode.IDENTITY_CREATION_FAILED);
    }

    return toResponse(saved);
  }


  private AppUserResponse toResponse(AppUserEntity entity) {
    return AppUserResponse.builder()
        .id(entity.getId())
        .username(entity.getUsername())
        .email(entity.getEmail())
        .name(entity.getDisplayName())
        .role(entity.getRole())
        .customerId(entity.getCustomerId())
        .isActive(entity.getIsActive())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }

  private void validateOwnerRegistrationRequest(AppUserRequest request) {
    if (request.getOnboardingCode() == null || request.getOnboardingCode().isBlank()) {
      throw new ArenaOpsException(ErrorCode.INVALID_REQUEST);
    }
    if (request.getEmail() == null || request.getEmail().isBlank()) {
      throw new ArenaOpsException(ErrorCode.INVALID_REQUEST);
    }
  }
}
