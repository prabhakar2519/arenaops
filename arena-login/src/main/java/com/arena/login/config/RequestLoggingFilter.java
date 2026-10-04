package com.arena.login.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Slf4j
public class RequestLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        log.info(">>> Incoming Request: {} {} from {}", request.getMethod(), path, request.getRemoteAddr());

        try {
            filterChain.doFilter(request, response);
        } finally {
            log.info("<<< Outgoing Response: {} {} - Status: {}", request.getMethod(), path, response.getStatus());
        }
    }
}
