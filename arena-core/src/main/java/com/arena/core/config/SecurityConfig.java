package com.arena.core.config;

import com.arena.core.exception.ErrorCode;
import com.arena.core.exception.ApiErrors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwkSetUri;

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    private final CustomerAccessFilter customerAccessFilter;
    private final ApiErrors apiErrors;

    public SecurityConfig(CustomerAccessFilter customerAccessFilter, ApiErrors apiErrors) {
        this.customerAccessFilter = customerAccessFilter;
        this.apiErrors = apiErrors;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(
                        org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        // Public endpoints
                        .requestMatchers(HttpMethod.POST, "/api/users").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/registration/validate-invitation").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/registration/activate").permitAll()
                        .requestMatchers("/api/health", "/actuator/health/readiness").permitAll()
                        // All other /api endpoints require a valid JWT
                        .requestMatchers("/api/**").authenticated()
                        // Catch-all
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.decoder(jwtDecoder()))
                        .authenticationEntryPoint((request, response, ex) -> apiErrors.write(request, response,
                                ErrorCode.AUTHENTICATION_REQUIRED))
                        .accessDeniedHandler((request, response, ex) -> apiErrors.write(request, response,
                                ErrorCode.ACCESS_DENIED)))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, ex) -> apiErrors.write(request, response,
                                ErrorCode.AUTHENTICATION_REQUIRED))
                        .accessDeniedHandler((request, response, ex) -> apiErrors.write(request, response,
                                ErrorCode.ACCESS_DENIED)))
                .addFilterAfter(customerAccessFilter, BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public org.springframework.boot.web.servlet.FilterRegistrationBean<CustomerAccessFilter> customerAccessRegistration() {
        var registration = new org.springframework.boot.web.servlet.FilterRegistrationBean<>(customerAccessFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        var decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(org.springframework.security.oauth2.jwt.JwtValidators.createDefaultWithIssuer(issuerUri));
        return decoder;
    }
}
