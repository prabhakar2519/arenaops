package com.arena.core.service;

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
      throw new IllegalArgumentException("Public registration only supports owner accounts");
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
      throw new IllegalArgumentException("User already exists: " + request.getUsername());
    }

    log.info("[AppUserService] Checking if user '{}' exists in Keycloak...", request.getUsername());
    boolean existsInKeycloak = keycloakService.existsInKeycloak(request.getUsername());
    log.info("[AppUserService] User '{}' existsInKeycloak={}", request.getUsername(), existsInKeycloak);
    if (existsInKeycloak) {
      throw new IllegalArgumentException("User already exists: " + request.getUsername());
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
    } catch (Exception e) {
      log.error("[AppUserService] Keycloak creation failed for '{}': {}", request.getUsername(), e.getMessage(), e);
      throw new RuntimeException("Failed to create user in Keycloak: " + e.getMessage(), e);
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
      throw new IllegalArgumentException("Activation code is required for owner registration");
    }
    if (request.getEmail() == null || request.getEmail().isBlank()) {
      throw new IllegalArgumentException("Email is required for owner registration");
    }
  }
}
