package com.arena.login.documentation;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
@Configuration
public class DocumentationSecurity {
 @Bean @Order(0)
 SecurityFilterChain documentationSecurityFilterChain(HttpSecurity http,@Value("${arena.api-docs.enabled:false}") boolean enabled)throws Exception{
  return http.securityMatcher("/swagger-ui/**","/swagger-ui.html","/v3/api-docs/**","/v3/api-docs.yaml")
   .csrf(c->c.disable()).authorizeHttpRequests(a->a.anyRequest().access((authentication,context)->new org.springframework.security.authorization.AuthorizationDecision(enabled)))
   .sessionManagement(s->s.sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.STATELESS)).build();
 }
}
