package com.arena.core.documentation;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.Map;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
class DocumentationSecurityTest {
 @Profile("openapi-contract") @Configuration @EnableWebMvc @EnableWebSecurity @Import(DocumentationSecurity.class)
 static class Beans {
  @Bean @Order(1) SecurityFilterChain fallback(HttpSecurity http)throws Exception{return http.authorizeHttpRequests(a->a.anyRequest().denyAll()).build();}
  @Bean Endpoints endpoints(){return new Endpoints();}
 }
 @Profile("openapi-contract") @RestController static class Endpoints {
  @GetMapping({"/v3/api-docs","/v3/api-docs.yaml","/swagger-ui/index.html"}) Map<String,String> docs(){return Map.of("status","test fixture");}
 }
 @Test void documentationIsOptInAndNeverOpensBusinessRoutes()throws Exception{
  for(boolean enabled:new boolean[]{false,true})try(var context=new AnnotationConfigWebApplicationContext()){
   context.setServletContext(new MockServletContext());context.getEnvironment().setActiveProfiles("openapi-contract");context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",Map.of("arena.api-docs.enabled",enabled)));context.register(Beans.class);context.refresh();
   var mvc=MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean("springSecurityFilterChain",jakarta.servlet.Filter.class)).build();
   for(String path:new String[]{"/v3/api-docs","/v3/api-docs.yaml","/swagger-ui/index.html"})mvc.perform(get(path)).andExpect(enabled?status().isOk():status().isForbidden());
   mvc.perform(get("/api/admin/customers")).andExpect(status().isForbidden());
  }
 }
}
