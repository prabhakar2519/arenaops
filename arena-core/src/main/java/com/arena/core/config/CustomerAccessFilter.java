package com.arena.core.config;

import com.arena.core.exception.ArenaOpsException;
import com.arena.core.exception.ErrorCode;
import com.arena.core.exception.ApiErrors;
import com.arena.core.exception.CorrelationIdFilter;

import com.arena.core.entity.AppUserEntity;
import com.arena.core.repository.AppUserRepository;
import com.arena.core.service.AdminAuthorizationService;
import com.arena.core.service.CustomerAccessService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class CustomerAccessFilter extends OncePerRequestFilter {

  private final AppUserRepository appUserRepository;
  private final CustomerAccessService customerAccessService;
  private final AdminAuthorizationService adminAuthorizationService;
  private final ApiErrors apiErrors;

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String path = request.getRequestURI();
    if (shouldSkip(path)) {
      filterChain.doFilter(request, response);
      return;
    }

    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication instanceof JwtAuthenticationToken jwtAuth) {
      Jwt jwt = jwtAuth.getToken();
      if (adminAuthorizationService.isAdmin(jwt)) {
        filterChain.doFilter(request, response);
        return;
      }

      String username = adminAuthorizationService.resolveUsername(jwt);

      try {
        AppUserEntity user = appUserRepository.findByUsername(username).orElse(null);
        if (user == null) {
          apiErrors.write(request, response, ErrorCode.CUSTOMER_ACCESS_BLOCKED);
          return;
        }
        customerAccessService.assertCustomerCanUseApp(user);
      } catch (ArenaOpsException ex) {
        apiErrors.write(request, response, ex.getErrorCode());
        return;
      } catch (RuntimeException ex) {
        // Do not expose persistence/provider exception messages from a servlet filter.
        org.slf4j.LoggerFactory.getLogger(CustomerAccessFilter.class).error(
            "Access evaluation failed correlationId={} type={} frames={}",
            CorrelationIdFilter.correlationId(request),
            ex.getClass().getSimpleName(), java.util.Arrays.stream(ex.getStackTrace()).limit(25).toList());
        apiErrors.write(request, response, ErrorCode.INTERNAL_SERVER_ERROR);
        return;
      }
    }

    filterChain.doFilter(request, response);
  }

  private boolean shouldSkip(String path) {
    return !path.startsWith("/api/")
        || path.equals("/api/health")
        || path.equals("/api/access/check")
        || path.equals("/api/billing")
        || path.startsWith("/api/billing/")
        || path.equals("/api/users")
        || path.startsWith("/api/admin/");
  }

}
