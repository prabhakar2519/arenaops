package com.arena.login.documentation;
import jakarta.servlet.ServletContext;
import org.springdoc.webmvc.ui.SwaggerWebMvcConfigurer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.handler.AbstractHandlerMapping;

/** Serve only springdoc assets before the BFF /** controller; never reprioritize general static resources. */
@Configuration
@ConditionalOnProperty(name="springdoc.swagger-ui.enabled",havingValue="true")
public class DocumentationResources {
 static class Registry extends ResourceHandlerRegistry {
  Registry(ApplicationContext context,ServletContext servlet){super(context,servlet);}
  AbstractHandlerMapping mapping(){return getHandlerMapping();}
 }
 @Bean AbstractHandlerMapping swaggerAssetHandlerMapping(ApplicationContext context,ServletContext servlet,SwaggerWebMvcConfigurer swagger){
  var registry=new Registry(context,servlet);swagger.addResourceHandlers(registry);registry.setOrder(-1);return registry.mapping();
 }
}
