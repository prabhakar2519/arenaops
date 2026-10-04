package com.arena.login.controller;

import com.arena.login.exception.*;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.web.client.ResourceAccessException;
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
    private final ObjectMapper errorMapper = new ObjectMapper();

    @Value("${arena.core.url:http://localhost:7701}")
    private String coreUrl;

    public TokenController(TokenService tokenService,
            @Qualifier("coreRestTemplate") RestTemplate restTemplate) {
        this.tokenService = tokenService;
        this.restTemplate = restTemplate;
    }

    @PostMapping({ "/token", "/token/" })
    public ResponseEntity<?> validateToken(@Valid @RequestBody TokenRequest request,
            HttpSession session, HttpServletRequest servletRequest) {
        try {
            log.info("Validating token for authorization code");

            // Exchange authorization code for access token with Keycloak
            TokenResponse tokenResponse = tokenService.exchangeCodeForToken(request.getCode(), request.getRedirectUri(), request.getCodeVerifier());

            UserInfo userInfo = tokenService.getUserInfoFromToken(tokenResponse.getAccessToken());
            validateCoreAccess(tokenResponse.getAccessToken());

            // Store tokens in HTTPSession (BFF Pattern)
            session.setAttribute("ACCESS_TOKEN", tokenResponse.getAccessToken());
            session.setAttribute("REFRESH_TOKEN", tokenResponse.getRefreshToken());
            session.setMaxInactiveInterval(7 * 60 * 60);
            touchPresence(session, tokenResponse.getAccessToken(), true);
            log.info("Stored tokens in session. Session ID: {}", session.getId());

            return ResponseEntity.ok(userInfo);
        } catch (ArenaOpsException e) {
            session.invalidate();
            return ApiErrors.response(e.getErrorCode(), servletRequest);
        } catch (HttpStatusCodeException e) {
            session.invalidate();
            return downstreamError(e, servletRequest);
        } catch (RuntimeException e) {
            session.invalidate();
            throw e;
        }
    }

    @GetMapping({ "/user", "/user/" })
    public ResponseEntity<?> getUserInfo(HttpSession session, HttpServletRequest servletRequest) {
        try {
            String token = (String) session.getAttribute("ACCESS_TOKEN");
            if (token == null) {
                log.warn("[SessionCheck] No ACCESS_TOKEN found in session: {}", session.getId());
                return ApiErrors.response(ErrorCode.AUTHENTICATION_REQUIRED, servletRequest);
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
        } catch (ArenaOpsException e) {
            if (e.getErrorCode().getStatus() == 401) session.invalidate();
            return ApiErrors.response(e.getErrorCode(), servletRequest);
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403) session.invalidate();
            return downstreamError(e, servletRequest);
        }
    }

    @PostMapping({ "/logout", "/logout/" })
    public ResponseEntity<Void> logout(HttpSession session) {
        String token = (String) session.getAttribute("ACCESS_TOKEN");
        if (token != null) {
            try {
                postCorePresence(token, "/api/presence/logout");
            } catch (Exception e) {
                log.warn("Could not mark user offline before logout type={}", e.getClass().getSimpleName());
            }
        }
        session.invalidate();
        return ResponseEntity.noContent().build();
    }

    @GetMapping({ "/health", "/health/" })
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }

    @GetMapping("/readiness")
    public ResponseEntity<?> readiness(HttpServletRequest request) {
        try {
            restTemplate.getForEntity(coreUrl + "/actuator/health/readiness", byte[].class);
            return ResponseEntity.ok(java.util.Map.of("status", "UP"));
        } catch (org.springframework.web.client.RestClientException e) {
            return ApiErrors.response(ErrorCode.SERVICE_UNAVAILABLE, request);
        }
    }

    private void validateCoreAccess(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        String correlationId = org.slf4j.MDC.get("correlationId");
        if (correlationId != null) headers.set(CorrelationIdFilter.HEADER, correlationId);
        try {
            restTemplate.exchange(
                coreUrl + "/api/access/check",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                byte[].class);
        } catch (ResourceAccessException e) {
            throw new ArenaOpsException(ErrorCode.SERVICE_UNAVAILABLE);
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().is5xxServerError()) {
                throw new ArenaOpsException(ErrorCode.SERVICE_UNAVAILABLE);
            }
            throw e;
        }
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

        if (java.util.Set.of("/api/token", "/token", "/api/user", "/user", "/api/logout", "/logout",
                "/api/health", "/health", "/api/readiness", "/readiness").contains(checkPath)) {
            // Valid methods use the specific handlers; wildcard routing must not turn unsupported methods into 200.
            return errorBytes(ErrorCode.METHOD_NOT_ALLOWED, request);
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
            return errorBytes(ErrorCode.AUTHENTICATION_REQUIRED, request);
        }

        if (accessToken != null) {
            touchPresence(session, accessToken, false);
        }

        // Hop-by-hop headers must NOT be forwarded to the downstream service
        java.util.Set<String> hopByHopHeaders = java.util.Set.of(
                "host", "connection", "keep-alive", "proxy-authenticate",
                "proxy-authorization", "te", "trailers", "transfer-encoding", "upgrade",
                "cookie", "authorization", "x-correlation-id",
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
        headers.set(CorrelationIdFilter.HEADER, CorrelationIdFilter.correlationId(request));
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
                    } catch (HttpStatusCodeException retryError) {
                        if (retryError.getStatusCode().value() == 401) session.invalidate();
                        return downstreamErrorBytes(retryError, request);
                    } catch (ArenaOpsException refreshError) {
                        if (refreshError.getErrorCode().getStatus() == 401) session.invalidate();
                        return errorBytes(refreshError.getErrorCode(), request);
                    } catch (ResourceAccessException unavailable) {
                        return errorBytes(ErrorCode.SERVICE_UNAVAILABLE, request);
                    }
                } else {
                    log.error("[BFF Proxy] No refresh token found in session {}. Cannot recover from 401.",
                            session.getId());
                }
            }

            return downstreamErrorBytes(e, request);
        } catch (ResourceAccessException unavailable) {
            return errorBytes(ErrorCode.SERVICE_UNAVAILABLE, request);
        }
    }

    private ResponseEntity<ErrorResponse> downstreamError(HttpStatusCodeException exception, HttpServletRequest request) {
        int status = exception.getStatusCode().value();
        ErrorCode code = ErrorCode.forStatus(status);
        java.util.Map<String, String> fields = null;
        try {
            var body = errorMapper.readTree(exception.getResponseBodyAsByteArray());
            var recognized = ErrorCode.valueOf(body.path("errorCode").asText());
            if (recognized.getStatus() == status) {
                code = recognized;
                if (code == ErrorCode.VALIDATION_FAILED && body.path("fieldErrors").isObject()) {
                    fields = errorMapper.convertValue(body.path("fieldErrors"),
                            new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, String>>() {});
                }
            }
        } catch (Exception ignored) {
            // Legacy/unstructured upstream errors get safe status-based descriptions.
        }
        return ResponseEntity.status(status).header(CorrelationIdFilter.HEADER, CorrelationIdFilter.correlationId(request))
                .body(ApiErrors.body(code, status, request, fields));
    }

    private ResponseEntity<byte[]> downstreamErrorBytes(HttpStatusCodeException exception, HttpServletRequest request) throws IOException {
        var response = downstreamError(exception, request);
        return ResponseEntity.status(response.getStatusCode()).contentType(MediaType.APPLICATION_JSON)
                .body(errorMapper.writeValueAsBytes(response.getBody()));
    }

    private ResponseEntity<byte[]> errorBytes(ErrorCode code, HttpServletRequest request) throws IOException {
        return ResponseEntity.status(code.getStatus()).contentType(MediaType.APPLICATION_JSON)
                .body(errorMapper.writeValueAsBytes(ApiErrors.body(code, code.getStatus(), request, null)));
    }

    private String refreshSessionToken(HttpSession session) {
        String refreshToken = (String) session.getAttribute("REFRESH_TOKEN");
        if (refreshToken == null) throw new ArenaOpsException(ErrorCode.AUTHENTICATION_REQUIRED);
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
            log.debug("Presence heartbeat failed type={}", e.getClass().getSimpleName());
        }
    }

    private void postCorePresence(String accessToken, String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        String correlationId = org.slf4j.MDC.get("correlationId");
        if (correlationId != null) headers.set(CorrelationIdFilter.HEADER, correlationId);
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
