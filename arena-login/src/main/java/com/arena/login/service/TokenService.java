package com.arena.login.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.arena.login.exception.ArenaOpsException;
import com.arena.login.exception.ErrorCode;
import com.arena.login.model.TokenResponse;
import com.arena.login.model.UserInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenService {

    @Value("${keycloak-admin.server-url}")
    private String serverUrl;

    @Value("${keycloak-admin.realm}")
    private String realm;

    @Value("${spring.security.oauth2.client.registration.keycloak.client-id}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.keycloak.redirect-uri}")
    private String redirectUri;


    private final RestTemplate restTemplate = identityClient();

    private static RestTemplate identityClient() {
        var factory = new org.springframework.http.client.JdkClientHttpRequestFactory(
                java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(5)).build());
        factory.setReadTimeout(java.time.Duration.ofSeconds(10));
        return new RestTemplate(factory);
    }
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TokenResponse exchangeCodeForToken(String code, String requestRedirectUri, String codeVerifier) {
        if (codeVerifier == null || !codeVerifier.matches("[A-Za-z0-9._~-]{43,128}")) {
            throw new ArenaOpsException(ErrorCode.AUTHENTICATION_FAILED);
        }
        String tokenUrl = serverUrl + "/realms/" + realm + "/protocol/openid-connect/token";
        String resolvedRedirectUri = requestRedirectUri == null || requestRedirectUri.isBlank()
                ? redirectUri
                : requestRedirectUri.trim();

        log.info("Exchanging authorization code for tokens at: {}", tokenUrl);
        log.debug("Using client_id: {}, redirect_uri: {}", clientId, resolvedRedirectUri);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("client_id", clientId);
        body.add("code", code);
        body.add("code_verifier", codeVerifier);
        body.add("redirect_uri", resolvedRedirectUri);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(tokenUrl, request, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode jsonNode = objectMapper.readTree(response.getBody());

                log.info("Successfully exchanged code for tokens");

                return TokenResponse.builder()
                        .accessToken(jsonNode.get("access_token").asText())
                        .refreshToken(jsonNode.has("refresh_token") ? jsonNode.get("refresh_token").asText() : null)
                        .idToken(jsonNode.has("id_token") ? jsonNode.get("id_token").asText() : null)
                        .expiresIn(jsonNode.get("expires_in").asInt())
                        .tokenType(jsonNode.get("token_type").asText())
                        .scope(jsonNode.has("scope") ? jsonNode.get("scope").asText() : null)
                        .build();
            } else {
                log.error("Token exchange failed with status: {}", response.getStatusCode());
                throw new ArenaOpsException(ErrorCode.AUTHENTICATION_FAILED);
            }
        } catch (org.springframework.web.client.ResourceAccessException e) {
            throw new ArenaOpsException(ErrorCode.IDENTITY_PROVIDER_UNAVAILABLE);
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            throw new ArenaOpsException(e.getStatusCode().is5xxServerError()
                    ? ErrorCode.IDENTITY_PROVIDER_UNAVAILABLE : ErrorCode.AUTHENTICATION_FAILED);
        } catch (ArenaOpsException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Identity response invalid type={}", e.getClass().getSimpleName());
            throw new ArenaOpsException(ErrorCode.IDENTITY_PROVIDER_UNAVAILABLE);
        }
    }

    public TokenResponse refreshToken(String refreshToken) {
        String tokenUrl = serverUrl + "/realms/" + realm + "/protocol/openid-connect/token";

        log.info("Refreshing access token using refresh token");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "refresh_token");
        body.add("client_id", clientId);
        body.add("refresh_token", refreshToken);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(tokenUrl, request, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode jsonNode = objectMapper.readTree(response.getBody());
                log.info("Successfully refreshed access token");

                return TokenResponse.builder()
                        .accessToken(jsonNode.get("access_token").asText())
                        .refreshToken(jsonNode.has("refresh_token") ? jsonNode.get("refresh_token").asText() : null)
                        .idToken(jsonNode.has("id_token") ? jsonNode.get("id_token").asText() : null)
                        .expiresIn(jsonNode.get("expires_in").asInt())
                        .tokenType(jsonNode.get("token_type").asText())
                        .scope(jsonNode.has("scope") ? jsonNode.get("scope").asText() : null)
                        .build();
            } else {
                log.error("Token refresh failed with status: {}", response.getStatusCode());
                throw new ArenaOpsException(ErrorCode.AUTHENTICATION_FAILED);
            }
        } catch (org.springframework.web.client.ResourceAccessException e) {
            throw new ArenaOpsException(ErrorCode.IDENTITY_PROVIDER_UNAVAILABLE);
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            throw new ArenaOpsException(e.getStatusCode().is5xxServerError()
                    ? ErrorCode.IDENTITY_PROVIDER_UNAVAILABLE : ErrorCode.AUTHENTICATION_FAILED);
        } catch (ArenaOpsException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Identity response invalid type={}", e.getClass().getSimpleName());
            throw new ArenaOpsException(ErrorCode.IDENTITY_PROVIDER_UNAVAILABLE);
        }
    }

    public UserInfo getUserInfoFromToken(String accessToken) {
        try {
            // Decode JWT token to extract user info
            String[] parts = accessToken.split("\\.");
            if (parts.length < 2) {
                throw new ArenaOpsException(ErrorCode.AUTHENTICATION_FAILED);
            }

            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
            JsonNode claims = objectMapper.readTree(payload);

            String username = claims.has("preferred_username")
                    ? claims.get("preferred_username").asText()
                    : claims.get("sub").asText();

            String email = claims.has("email") ? claims.get("email").asText() : null;
            String name = claims.has("name") ? claims.get("name").asText() : username;

            // Extract roles from realm_access
            List<String> roles = new ArrayList<>();
            if (claims.has("realm_access")) {
                JsonNode realmAccess = claims.get("realm_access");
                if (realmAccess.has("roles")) {
                    realmAccess.get("roles").forEach(role -> roles.add(role.asText()));
                }
            }

            String primaryRole = List.of("ADMIN", "OWNER", "COACH", "STAFF").stream()
                    .filter(roles::contains).findFirst().orElse("USER");

            return UserInfo.builder()
                    .username(username)
                    .email(email)
                    .name(name)
                    .role(primaryRole)
                    .roles(roles)
                    .build();

        } catch (Exception e) {
            throw new ArenaOpsException(ErrorCode.AUTHENTICATION_FAILED);
        }
    }

}
