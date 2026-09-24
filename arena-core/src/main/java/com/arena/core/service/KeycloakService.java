package com.arena.core.service;

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
            log.error(
                    "[KeycloakService] Failed to initialize Keycloak Admin Client. serverUrl={}, realm={}, clientId={}. Error: {}",
                    serverUrl, realm, clientId, e.getMessage());
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
        Response response = usersResource.create(user);
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
            throw new RuntimeException("User already exists in Keycloak: " + username);
        } else {
            String errorBody = response.readEntity(String.class);
            log.error("[KeycloakService] Failed to create user in Keycloak. Status: {}, Body: {}", status, errorBody);
            throw new RuntimeException(
                    "Failed to create user in Keycloak. Status: " + status + ", Error: " + errorBody);
        }
    }

    private void assignRole(RealmResource realmResource, String userId, String roleName) {
        log.info("[KeycloakService] Assigning role '{}' to user id={}", roleName, userId);
        try {
            RoleRepresentation role = realmResource.roles().get(roleName).toRepresentation();
            realmResource.users().get(userId).roles().realmLevel().add(Collections.singletonList(role));
            log.info("[KeycloakService] Role '{}' assigned successfully to user id={}", roleName, userId);
        } catch (Exception e) {
            log.error("[KeycloakService] Failed to assign role '{}' to user id={}: {}", roleName, userId,
                    e.getMessage(), e);
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
            log.error("[KeycloakService] Error checking user existence in Keycloak for '{}': {}", username,
                    e.getMessage(), e);
            throw e;
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
                .orElseThrow(() -> new RuntimeException("User not found in Keycloak: " + username));
    }

    public void deleteUser(String username) {
        deleteUserById(getKeycloakUserId(username));
    }

    private void deleteUserById(String userId) {
        try {
            keycloak.realm(realm).users().get(userId).remove();
            log.warn("[KeycloakService] Rolled back Keycloak user id={}", userId);
        } catch (Exception cleanupError) {
            log.error("[KeycloakService] Failed to roll back Keycloak user id={}: {}",
                    userId, cleanupError.getMessage(), cleanupError);
        }
    }
}
