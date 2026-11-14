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

    @Value("${app.email.frontend-url}")
    private String frontendUrl;

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

    /** Reuse an existing exact identity; invitations never choose or store a password. */
    public String ensureStaffIdentity(String username, String email, String name, String roleName) {
        if (!java.util.Set.of("COACH", "STAFF").contains(roleName))
            throw new ArenaOpsException(ErrorCode.ACCESS_DENIED);
        try {
            RealmResource resource = keycloak.realm(realm);
            java.util.List<UserRepresentation> matches = resource.users().search(username, true).stream()
                    .filter(u -> username.equalsIgnoreCase(u.getUsername())).toList();
            if (matches.size() > 1) throw new ArenaOpsException(ErrorCode.CONFLICT);
            String id;
            if (!matches.isEmpty()) {
                UserRepresentation existing = matches.get(0);
                if (existing.getEmail() == null || !email.equalsIgnoreCase(existing.getEmail()))
                    throw new ArenaOpsException(ErrorCode.INVALID_REQUEST);
                if (existing.getServiceAccountClientId() != null)
                    throw new ArenaOpsException(ErrorCode.ACCESS_DENIED);
                id = existing.getId();
            } else {
                UserRepresentation invited = new UserRepresentation();
                invited.setUsername(username); invited.setEmail(email); invited.setFirstName(name);
                invited.setEnabled(true); invited.setEmailVerified(false);
                invited.setRequiredActions(java.util.List.of("UPDATE_PASSWORD", "VERIFY_EMAIL"));
                try (Response response = resource.users().create(invited)) {
                    if (response.getStatus() == 409) throw new ArenaOpsException(ErrorCode.USER_ALREADY_EXISTS);
                    if (response.getStatus() != 201) throw new ArenaOpsException(ErrorCode.IDENTITY_CREATION_FAILED);
                    id = response.getLocation().getPath().replaceAll(".*/([^/]+)$", "$1");
                }
            }
            assignRole(resource, id, roleName);
            return id;
        } catch (ArenaOpsException ex) { throw ex; }
        catch (RuntimeException ex) {
            logIdentityFailure("staff identity", ex);
            throw new ArenaOpsException(ErrorCode.IDENTITY_PROVIDER_UNAVAILABLE);
        }
    }

    public void inviteStaff(String username) {
        try {
            keycloak.realm(realm).users().get(getKeycloakUserId(username))
                    .executeActionsEmail(java.util.List.of("UPDATE_PASSWORD", "VERIFY_EMAIL"));
        } catch (RuntimeException ex) {
            logIdentityFailure("staff invitation", ex);
            throw new ArenaOpsException(ErrorCode.EMAIL_DELIVERY_FAILED);
        }
    }

    /** Authenticated staff administration only. ArenaOps app_user enforces one account per email,
     * even in realms that permit duplicate emails. Never adopts an unrelated identity. */
    public void validateAcademyStaffIdentifiers(String username,String email,Long academyId,Long staffId) {
        try {
            var resource=keycloak.realm(realm).users();
            for(var u:resource.search(username,true))if(username.equalsIgnoreCase(u.getUsername())&&!ownedStaffIdentity(staffId!=null&&u.getId()!=null?resource.get(u.getId()).toRepresentation():u,academyId,staffId,email))throw new ArenaOpsException(ErrorCode.DUPLICATE_USERNAME);
            for(var u:resource.searchByEmail(email,true))if(u.getEmail()!=null&&email.equalsIgnoreCase(u.getEmail().trim())&&!ownedStaffIdentity(staffId!=null&&u.getId()!=null?resource.get(u.getId()).toRepresentation():u,academyId,staffId,email))throw new ArenaOpsException(ErrorCode.DUPLICATE_EMAIL);
        } catch(ArenaOpsException ex){throw ex;}catch(RuntimeException ex){logIdentityFailure("staff identifier validation",ex);throw new ArenaOpsException(ErrorCode.IDENTITY_PROVIDER_UNAVAILABLE);}
    }
    private boolean ownedStaffIdentity(UserRepresentation u,Long academyId,Long staffId,String email) {
        var attrs=u.getAttributes();return academyId!=null&&staffId!=null&&attrs!=null&&java.util.List.of(academyId.toString()).equals(attrs.get("arenaAcademyId"))&&java.util.List.of(staffId.toString()).equals(attrs.get("arenaStaffId"))&&email.equalsIgnoreCase(u.getEmail())&&u.getServiceAccountClientId()==null;
    }

    /** Only recover an identity previously provisioned for this exact academy personnel record. */
    public String provisionAcademyStaff(String username,String email,String name,Long academyId,Long staffId,String academyName) {
        try {
            validateAcademyStaffIdentifiers(username,email,academyId,staffId);
            RealmResource resource=keycloak.realm(realm);
            var matches=resource.users().search(username,true).stream().filter(u->username.equalsIgnoreCase(u.getUsername())).toList();
            String id;
            if(!matches.isEmpty()) {
                if(matches.size()!=1)throw new ArenaOpsException(ErrorCode.CONFLICT);
                var existing=resource.users().get(matches.get(0).getId()).toRepresentation();
                var attrs=existing.getAttributes();
                if(attrs==null || !java.util.List.of(academyId.toString()).equals(attrs.get("arenaAcademyId")) || !java.util.List.of(staffId.toString()).equals(attrs.get("arenaStaffId")) || !email.equalsIgnoreCase(existing.getEmail()) || existing.getServiceAccountClientId()!=null)
                    throw new ArenaOpsException(ErrorCode.DUPLICATE_USERNAME);
                id=existing.getId();
            } else {
                var invited=new UserRepresentation();invited.setUsername(username);invited.setEmail(email);invited.setFirstName(name);invited.setEnabled(true);invited.setEmailVerified(false);
                invited.setRequiredActions(java.util.List.of("VERIFY_EMAIL","UPDATE_PASSWORD","ARENA_STAFF_SETUP"));
                invited.setAttributes(java.util.Map.of("arenaAcademyId",java.util.List.of(academyId.toString()),"arenaStaffId",java.util.List.of(staffId.toString()),"arenaAcademyName",java.util.List.of(academyName)));
                try(Response response=resource.users().create(invited)) {
                    if(response.getStatus()==409){validateAcademyStaffIdentifiers(username,email,academyId,staffId);throw new ArenaOpsException(ErrorCode.USER_ALREADY_EXISTS);}
                    if(response.getStatus()!=201)throw new ArenaOpsException(ErrorCode.IDENTITY_CREATION_FAILED);
                    id=response.getLocation().getPath().replaceAll(".*/([^/]+)$","$1");
                }
            }
            return id;
        } catch(ArenaOpsException ex){throw ex;} catch(RuntimeException ex){logIdentityFailure("academy staff provision",ex);throw new ArenaOpsException(ErrorCode.IDENTITY_PROVIDER_UNAVAILABLE);}
    }
    public boolean academyStaffSetupComplete(String id) {
        try {var user=keycloak.realm(realm).users().get(id).toRepresentation();return Boolean.TRUE.equals(user.isEnabled())&&Boolean.TRUE.equals(user.isEmailVerified())&&(user.getRequiredActions()==null||user.getRequiredActions().isEmpty());}
        catch(RuntimeException ex){logIdentityFailure("academy setup verification",ex);throw new ArenaOpsException(ErrorCode.IDENTITY_PROVIDER_UNAVAILABLE);}
    }
    public void sendAcademySetupEmail(String id,int lifespan) {
        String stage="REQUIRED_ACTION_LOOKUP";
        String configurationFailure=null;
        try {
            var resource=keycloak.realm(realm);
            var action=resource.flows().getRequiredAction("ARENA_STAFF_SETUP");
            if(!action.isEnabled()||!"ARENA_STAFF_SETUP".equals(action.getProviderId())) {
                configurationFailure=!action.isEnabled()?"REQUIRED_ACTION_DISABLED":"REQUIRED_ACTION_PROVIDER_MISMATCH";
                throw new ArenaOpsException(ErrorCode.EMAIL_DELIVERY_FAILED);
            }
            stage="EXECUTE_ACTIONS_EMAIL";
            resource.users().get(id).executeActionsEmail("arena-ui",frontendUrl.replaceAll("/+$", "")+"/login",lifespan,java.util.List.of("VERIFY_EMAIL","UPDATE_PASSWORD","ARENA_STAFF_SETUP"));
        } catch(RuntimeException ex) {
            Integer status=null;
            var seen=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable,Boolean>());
            for(Throwable cause=ex;cause!=null&&seen.size()<10&&seen.add(cause);cause=cause.getCause()) {
                if(cause instanceof jakarta.ws.rs.WebApplicationException http && http.getResponse()!=null) {
                    status=http.getResponse().getStatus();break;
                }
            }
            String reason=configurationFailure;
            if(reason==null) {
                if(Integer.valueOf(404).equals(status))reason="REQUIRED_ACTION_LOOKUP".equals(stage)?"REQUIRED_ACTION_NOT_REGISTERED":"KEYCLOAK_USER_OR_EMAIL_ENDPOINT_NOT_FOUND";
                else if(Integer.valueOf(401).equals(status)||Integer.valueOf(403).equals(status))reason="KEYCLOAK_ADMIN_AUTHORIZATION_FAILED";
                else if("EXECUTE_ACTIONS_EMAIL".equals(stage)&&status!=null&&status>=500)reason="KEYCLOAK_EMAIL_SEND_FAILED_CHECK_SERVER_SMTP_AND_THEME_LOGS";
                else if(Integer.valueOf(400).equals(status))reason="KEYCLOAK_REJECTED_EMAIL_OR_REQUIRED_ACTION_CONFIGURATION";
                else reason="KEYCLOAK_REQUEST_FAILED";
            }
            // Never include raw provider response bodies, exception messages, credentials or action links.
            log.warn("Academy setup email failed stage={} reason={} requiredAction=ARENA_STAFF_SETUP realm={} identityId={} httpStatus={} cause={}",
                    stage,reason,realm,id,status,ex.getClass().getSimpleName());
            logIdentityFailure("academy setup email "+stage,ex);
            throw new ArenaOpsException(ErrorCode.EMAIL_DELIVERY_FAILED);
        }
    }
    public void setAcademyIdentityEnabled(String id,boolean enabled) {
        try {var resource=keycloak.realm(realm).users().get(id);var user=resource.toRepresentation();user.setEnabled(enabled);resource.update(user);if(!enabled)resource.logout();}
        catch(RuntimeException ex){logIdentityFailure("academy identity access",ex);throw new ArenaOpsException(ErrorCode.IDENTITY_PROVIDER_UNAVAILABLE);}
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
