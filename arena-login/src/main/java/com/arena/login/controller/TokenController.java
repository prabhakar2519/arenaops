package com.arena.login.controller;

import com.arena.login.model.TokenRequest;
import com.arena.login.model.TokenResponse;
import com.arena.login.model.UserInfo;
import com.arena.login.service.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Qualifier;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Enumeration;
import java.time.Instant;

@RestController
@RequestMapping({ "/api", "" })
@Slf4j
public class TokenController {

    private final TokenService tokenService;
    private final RestTemplate restTemplate;

    @Value("${arena.core.url:http://localhost:7701}")
    private String coreUrl;

    public TokenController(TokenService tokenService,
            @Qualifier("coreRestTemplate") RestTemplate restTemplate) {
        this.tokenService = tokenService;
        this.restTemplate = restTemplate;
    }

    @PostMapping({ "/token", "/token/" })
    public ResponseEntity<?> validateToken(@Valid @RequestBody TokenRequest request,
            HttpSession session) {
        try {
            log.info("Validating token for authorization code");

            // Exchange authorization code for access token with Keycloak
            TokenResponse tokenResponse = tokenService.exchangeCodeForToken(request.getCode(), request.getRedirectUri());

            UserInfo userInfo = tokenService.getUserInfoFromToken(tokenResponse.getAccessToken());
            validateCoreAccess(tokenResponse.getAccessToken());

            // Store tokens in HTTPSession (BFF Pattern)
            session.setAttribute("ACCESS_TOKEN", tokenResponse.getAccessToken());
            session.setAttribute("REFRESH_TOKEN", tokenResponse.getRefreshToken());
            session.setMaxInactiveInterval(7 * 60 * 60);
            touchPresence(session, tokenResponse.getAccessToken(), true);
            log.info("Stored tokens in session. Session ID: {}", session.getId());

            return ResponseEntity.ok(userInfo);
        } catch (HttpStatusCodeException e) {
            session.invalidate();
            log.warn("ArenaOps access denied during login: status={}, body={}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            return ResponseEntity.status(e.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Token validation failed: {}", e.getMessage());
            session.invalidate();
            return ResponseEntity.status(401).build();
        }
    }

    @GetMapping({ "/user", "/user/" })
    public ResponseEntity<?> getUserInfo(HttpSession session) {
        try {
            String token = (String) session.getAttribute("ACCESS_TOKEN");
            if (token == null) {
                log.warn("[SessionCheck] No ACCESS_TOKEN found in session: {}", session.getId());
                return ResponseEntity.status(401).build();
            }

            UserInfo userInfo = tokenService.getUserInfoFromToken(token);
            try {
                validateCoreAccess(token);
            } catch (HttpStatusCodeException e) {
                if (e.getStatusCode() != HttpStatus.UNAUTHORIZED) throw e;
                token = refreshSessionToken(session);
                userInfo = tokenService.getUserInfoFromToken(token);
                validateCoreAccess(token);
            }
            touchPresence(session, token, false);
            log.debug("[SessionCheck] Successfully retrieved user info for: {}", userInfo.getUsername());

            return ResponseEntity.ok(userInfo);
        } catch (HttpStatusCodeException e) {
            log.warn("[SessionCheck] ArenaOps access denied for session {}: status={}, body={}",
                    session.getId(), e.getStatusCode(), e.getResponseBodyAsString());
            session.invalidate();
            return ResponseEntity.status(e.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("[SessionCheck] Failed to get user info for session {}: {}", session.getId(), e.getMessage());
            return ResponseEntity.status(401).build();
        }
    }

    @PostMapping({ "/logout", "/logout/" })
    public ResponseEntity<Void> logout(HttpSession session) {
        String token = (String) session.getAttribute("ACCESS_TOKEN");
        if (token != null) {
            try {
                postCorePresence(token, "/api/presence/logout");
            } catch (Exception e) {
                log.warn("Could not mark user offline before logout: {}", e.getMessage());
            }
        }
        session.invalidate();
        return ResponseEntity.noContent().build();
    }

    @GetMapping({ "/health", "/health/" })
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }

    private void validateCoreAccess(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        restTemplate.exchange(
                coreUrl + "/api/access/check",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                byte[].class);
    }

    @RequestMapping("/**")
    public ResponseEntity<byte[]> proxyRequest(HttpServletRequest request, HttpSession session)
            throws URISyntaxException, IOException {
        String path = request.getRequestURI();

        // Skip endpoints handled specifically in this controller
        String checkPath = path;
        if (checkPath.endsWith("/")) {
            checkPath = checkPath.substring(0, checkPath.length() - 1);
        }

        if (checkPath.endsWith("/token") || checkPath.endsWith("/user") || checkPath.endsWith("/logout") || checkPath.endsWith("/health")
                || checkPath.endsWith("/error")) {
            return null; // Should not be hit due to priority
        }

        // Normalize path to ensure it starts with /api before proxying
        if (!path.startsWith("/api/") && !path.equals("/api")) {
            path = "/api" + (path.startsWith("/") ? "" : "/") + path;
        }

        String query = request.getQueryString();
        String targetUri = coreUrl + path + (query != null ? "?" + query : "");

        String accessToken = (String) session.getAttribute("ACCESS_TOKEN");
        log.info("[BFF Proxy] {} {} - SessionID: {}, TokenPresent: {}",
                request.getMethod(), path, session.getId(), accessToken != null);

        // Allow initial registration and public booking to be proxied without a token
        boolean isInitialRegistration = request.getMethod().equalsIgnoreCase("POST")
                && java.util.Set.of("/api/users", "/api/registration/validate-invitation",
                        "/api/registration/activate").contains(path);
        boolean isPublicApi = path.startsWith("/api/public/");

        if (accessToken == null && !isInitialRegistration && !isPublicApi) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (accessToken != null) {
            touchPresence(session, accessToken, false);
        }

        // Hop-by-hop headers must NOT be forwarded to the downstream service
        java.util.Set<String> hopByHopHeaders = java.util.Set.of(
                "host", "connection", "keep-alive", "proxy-authenticate",
                "proxy-authorization", "te", "trailers", "transfer-encoding", "upgrade",
                "content-length" // we will set this ourselves from the byte array
        );

        HttpHeaders headers = new HttpHeaders();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            if (!hopByHopHeaders.contains(headerName.toLowerCase())) {
                headers.addAll(headerName, Collections.list(request.getHeaders(headerName)));
            }
        }
        if (accessToken != null) {
            headers.setBearerAuth(accessToken);
        }

        byte[] body = StreamUtils.copyToByteArray(request.getInputStream());
        headers.setContentLength(body.length);
        HttpEntity<byte[]> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<byte[]> response = restTemplate.exchange(new URI(targetUri),
                    HttpMethod.valueOf(request.getMethod()), entity,
                    byte[].class);
            log.info("Proxy response from core: {} for path: {}", response.getStatusCode(), path);
            return buildCleanResponse(response.getStatusCode(), response.getHeaders(), response.getBody());
        } catch (HttpStatusCodeException e) {
            // If spms-core returns 401, try to refresh the access token and retry once
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED && accessToken != null) {
                log.warn("[BFF Proxy] Received 401 from core for {}. Attempting token refresh...", path);
                String refreshTokenValue = (String) session.getAttribute("REFRESH_TOKEN");

                if (refreshTokenValue != null) {
                    try {
                        com.arena.login.model.TokenResponse refreshed = tokenService.refreshToken(refreshTokenValue);

                        // Update session with new tokens
                        session.setAttribute("ACCESS_TOKEN", refreshed.getAccessToken());
                        if (refreshed.getRefreshToken() != null) {
                            session.setAttribute("REFRESH_TOKEN", refreshed.getRefreshToken());
                        }
                        touchPresence(session, refreshed.getAccessToken(), true);
                        log.info("[BFF Proxy] Token refreshed successfully for session: {}. Retrying request: {}",
                                session.getId(), path);

                        // Retry with new access token
                        headers.setBearerAuth(refreshed.getAccessToken());
                        HttpEntity<byte[]> retryEntity = new HttpEntity<>(body, headers);
                        ResponseEntity<byte[]> retryResponse = restTemplate.exchange(new URI(targetUri),
                                HttpMethod.valueOf(request.getMethod()), retryEntity,
                                byte[].class);
                        log.info("[BFF Proxy] Retry successful. Status: {}", retryResponse.getStatusCode());
                        return buildCleanResponse(retryResponse.getStatusCode(), retryResponse.getHeaders(),
                                retryResponse.getBody());
                    } catch (Exception refreshEx) {
                        log.error("[BFF Proxy] Token refresh failed for session {}: {}. Forcing logout.",
                                session.getId(), refreshEx.getMessage());
                        // Refresh token also expired — force re-login
                        session.invalidate();
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
                    }
                } else {
                    log.error("[BFF Proxy] No refresh token found in session {}. Cannot recover from 401.",
                            session.getId());
                }
            }

            log.error("Proxy error from core: {} for path: {}. Body: {}", e.getStatusCode(), path,
                    e.getResponseBodyAsString());
            return buildCleanResponse(e.getStatusCode(), e.getResponseHeaders(), e.getResponseBodyAsByteArray());
        } catch (Exception e) {
            log.error("Proxy failure: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private String refreshSessionToken(HttpSession session) {
        String refreshToken = (String) session.getAttribute("REFRESH_TOKEN");
        if (refreshToken == null) throw new IllegalStateException("No refresh token available");
        TokenResponse refreshed = tokenService.refreshToken(refreshToken);
        session.setAttribute("ACCESS_TOKEN", refreshed.getAccessToken());
        if (refreshed.getRefreshToken() != null) session.setAttribute("REFRESH_TOKEN", refreshed.getRefreshToken());
        return refreshed.getAccessToken();
    }

    private void touchPresence(HttpSession session, String accessToken, boolean force) {
        Long lastTouch = (Long) session.getAttribute("LAST_PRESENCE_TOUCH");
        long now = Instant.now().getEpochSecond();
        if (!force && lastTouch != null && now - lastTouch < 60) return;
        try {
            postCorePresence(accessToken, "/api/presence/heartbeat");
            session.setAttribute("LAST_PRESENCE_TOUCH", now);
        } catch (Exception e) {
            log.debug("Presence heartbeat failed: {}", e.getMessage());
        }
    }

    private void postCorePresence(String accessToken, String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        restTemplate.exchange(coreUrl + path, HttpMethod.POST, new HttpEntity<>(headers), Void.class);
    }

    private ResponseEntity<byte[]> buildCleanResponse(org.springframework.http.HttpStatusCode status,
            HttpHeaders sourceHeaders, byte[] body) {
        // Strip hop-by-hop headers from the response — RestTemplate already decoded
        // the chunked body into a byte[], so forwarding transfer-encoding: chunked
        // would cause Spring to double-encode it, producing a corrupt response.
        java.util.Set<String> responseHopByHop = java.util.Set.of(
                "transfer-encoding", "connection", "keep-alive",
                "proxy-authenticate", "proxy-authorization", "te", "trailers", "upgrade");

        HttpHeaders cleanHeaders = new HttpHeaders();
        if (sourceHeaders != null) {
            sourceHeaders.forEach((name, values) -> {
                if (!responseHopByHop.contains(name.toLowerCase())) {
                    cleanHeaders.addAll(name, values);
                }
            });
        }
        return ResponseEntity.status(status).headers(cleanHeaders).body(body);
    }
}
