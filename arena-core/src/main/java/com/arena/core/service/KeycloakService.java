package com.arena.core.service;

import com.arena.core.exception.ArenaOpsException;
import com.arena.core.exception.ErrorCode;
import jakarta.annotation.PostConstruct;
import jakarta.ws.rs.core.Response;
import java.util.Collections;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class KeycloakService {

    @Value("${keycloak-admin.server-url}")
    private String serverUrl;

    @Value("${keycloak-admin.realm}")
    private String realm;

    @Value("${keycloak-admin.client-id}")
    private String clientId;

    @Value("${keycloak-admin.client-secret}")
    private String clientSecret;

    private Keycloak keycloak;

    @PostConstruct
    public void init() {
        log.info("[KeycloakService] Initializing Keycloak Admin Client - serverUrl={}, realm={}, clientId={}",
                serverUrl, realm, clientId);

        org.jboss.resteasy.client.jaxrs.ResteasyClient client = (org.jboss.resteasy.client.jaxrs.ResteasyClient) org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder
                .newBuilder()
                .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .build();

        try {
            this.keycloak = KeycloakBuilder.builder()
                    .serverUrl(serverUrl)
                    .realm(realm)
                    .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                    .clientId(clientId)
                    .clientSecret(clientSecret)
                    .resteasyClient(client)
                    .build();
            log.info("[KeycloakService] Keycloak Admin Client initialized successfully");
        } catch (Exception e) {
            log.error("Identity client initialization failed type={}", e.getClass().getSimpleName());
            throw e;
        }
    }

    public String createUser(String username, String password, String email, String roleName) {
        log.info("[KeycloakService] Creating user in Keycloak - username={}, email={}, role={}", username, email,
                roleName);
        log.info("[KeycloakService] Calling Keycloak Admin API at: {}/admin/realms/{}/users", serverUrl, realm);

        UserRepresentation user = new UserRepresentation();
        user.setUsername(username);
        user.setEmail(email);
        user.setEnabled(true);
        user.setEmailVerified(true);

        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(password);
        credential.setTemporary(false);
        user.setCredentials(Collections.singletonList(credential));

        RealmResource realmResource = keycloak.realm(realm);
        UsersResource usersResource = realmResource.users();

        log.info("[KeycloakService] Sending POST to Keycloak users API...");
        try (Response response = usersResource.create(user)) {
            int status = response.getStatus();
            log.info("[KeycloakService] Keycloak create user response status: {}", status);

            if (status == 201) {
                String userId = response.getLocation().getPath().replaceAll(".*/([^/]+)$", "$1");
                log.info("[KeycloakService] User created in Keycloak with ID: {}", userId);
                try {
                    assignRole(realmResource, userId, roleName);
                } catch (Exception e) {
                    deleteUserById(userId);
                    throw e;
                }
                return userId;
            } else if (status == 409) {
                log.warn("[KeycloakService] User already exists in Keycloak: {}", username);
                throw new ArenaOpsException(ErrorCode.USER_ALREADY_EXISTS);
            } else {
                log.warn("Identity creation rejected status={}", status);
                throw new ArenaOpsException(status >= 500 ? ErrorCode.IDENTITY_PROVIDER_UNAVAILABLE : ErrorCode.IDENTITY_CREATION_FAILED);
            }
        } catch (ArenaOpsException e) {
            throw e;
        } catch (jakarta.ws.rs.ProcessingException e) {
            throw new ArenaOpsException(ErrorCode.IDENTITY_PROVIDER_UNAVAILABLE);
        } catch (RuntimeException e) {
            log.warn("Identity creation failed type={}", e.getClass().getSimpleName());
            throw new ArenaOpsException(ErrorCode.IDENTITY_CREATION_FAILED);
        }
    }

    private void assignRole(RealmResource realmResource, String userId, String roleName) {
        log.info("[KeycloakService] Assigning role '{}' to user id={}", roleName, userId);
        try {
            RoleRepresentation role = realmResource.roles().get(roleName).toRepresentation();
            realmResource.users().get(userId).roles().realmLevel().add(Collections.singletonList(role));
            log.info("[KeycloakService] Role '{}' assigned successfully to user id={}", roleName, userId);
        } catch (Exception e) {
            log.warn("Identity role assignment failed userId={} type={}", userId, e.getClass().getSimpleName());
            throw e;
        }
    }

    public boolean existsInKeycloak(String username) {
        log.info("[KeycloakService] Checking if user exists in Keycloak: {}", username);
        try {
            boolean exists = keycloak.realm(realm)
                    .users()
                    .search(username, true)
                    .stream()
                    .anyMatch(u -> username.equalsIgnoreCase(u.getUsername()));
            log.info("[KeycloakService] User '{}' exists in Keycloak: {}", username, exists);
            return exists;
        } catch (Exception e) {
            logIdentityFailure("user lookup", e);
            throw new ArenaOpsException(ErrorCode.IDENTITY_PROVIDER_UNAVAILABLE);
        }
    }

    private void logIdentityFailure(String operation, Throwable failure) {
        var seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable, Boolean>());
        Throwable cause = failure;
        while (cause != null && seen.size() < 10 && seen.add(cause)) {
            Integer status = cause instanceof jakarta.ws.rs.WebApplicationException http
                    && http.getResponse() != null ? http.getResponse().getStatus() : null;
            // Never log exception messages: provider responses may contain credentials or tokens.
            log.warn("Identity operation={} failed cause={} httpStatus={}", operation,
                    cause.getClass().getSimpleName(), status);
            cause = cause.getCause();
        }
    }

    public String getKeycloakUserId(String username) {
        log.info("[KeycloakService] Fetching Keycloak user ID for username: {}", username);
        return keycloak.realm(realm)
                .users()
                .search(username, true)
                .stream()
                .filter(u -> username.equalsIgnoreCase(u.getUsername()))
                .findFirst()
                .map(UserRepresentation::getId)
                .orElseThrow(() -> new ArenaOpsException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    public void deleteUser(String username) {
        deleteUserById(getKeycloakUserId(username));
    }

    private void deleteUserById(String userId) {
        try {
            keycloak.realm(realm).users().get(userId).remove();
            log.warn("[KeycloakService] Rolled back Keycloak user id={}", userId);
        } catch (Exception cleanupError) {
            log.error("Identity cleanup failed userId={} type={}", userId, cleanupError.getClass().getSimpleName());
        }
    }
}
