package com.arena.login.config;

import com.arena.login.exception.ErrorCode;
import com.arena.login.exception.ApiErrors;

import org.springframework.boot.web.servlet.server.CookieSameSiteSupplier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
        private final ApiErrors apiErrors;

        public SecurityConfig(ApiErrors apiErrors) {
                this.apiErrors = apiErrors;
        }

        @org.springframework.beans.factory.annotation.Value("${app.cors.allowed-origins:http://localhost:4200,http://localhost:3000}")
        private String allowedOriginsConfig;

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                http
                                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                                .csrf(csrf -> csrf.disable())
                                .exceptionHandling(errors -> errors
                                        .authenticationEntryPoint((request, response, ex) -> apiErrors.write(request, response,
                                                ErrorCode.AUTHENTICATION_REQUIRED))
                                        .accessDeniedHandler((request, response, ex) -> apiErrors.write(request, response,
                                                ErrorCode.ACCESS_DENIED)))
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                                .authorizeHttpRequests(
                                                auth -> auth
                                                                // All /api/** requests are handled by controllers
                                                                // (TokenController does its own session auth check)
                                                                .requestMatchers("/**")
                                                                .permitAll()
                                                                .anyRequest()
                                                                .authenticated());

                return http.build();
        }

        /**
         * Ensures JSESSIONID cookie is sent by the browser on cross-origin requests
         * from localhost:4200 → localhost:7700 (Angular dev proxy scenario).
         */
        @Bean
        public CookieSameSiteSupplier cookieSameSiteSupplier() {
                return CookieSameSiteSupplier.ofLax();
        }

        @Bean
        public CorsConfigurationSource corsConfigurationSource() {
                CorsConfiguration configuration = new CorsConfiguration();
                List<String> origins = new ArrayList<>(Arrays.asList(allowedOriginsConfig.split(",")));
                configuration.setAllowedOrigins(origins);
                configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
                configuration.setAllowedHeaders(Arrays.asList("*"));
                configuration.setAllowCredentials(true);

                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/**", configuration);
                return source;
        }
}
