package com.arena.login.documentation;
import com.arena.login.exception.*;
import io.swagger.v3.core.converter.*;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.*;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.responses.*;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.security.*;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@Configuration
public class OpenApiConfiguration {
 @Bean OpenAPI arenaApi(){
  var components=new Components().addSecuritySchemes("sessionCookie",new SecurityScheme().type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.COOKIE).name("JSESSIONID").description("Session established by POST /api/token; tokens remain server-side."));
  var resolved=ModelConverters.getInstance(false).resolveAsResolvedSchema(new AnnotatedType(ErrorResponse.class).resolveAsRef(true));
  var errorSchema=resolved.referencedSchemas.get("ErrorResponse");
  if(errorSchema!=null && errorSchema.getProperties()!=null){
   var fieldErrors=errorSchema.getProperties().get("fieldErrors");
   if(fieldErrors instanceof Schema<?> schema){
    schema.setType("object");
    schema.setAdditionalProperties(new Schema<>().type("string"));
    schema.setDescription("Validation errors keyed by field name.");
   }
  }
  resolved.referencedSchemas.forEach(components::addSchemas);
  var api=new OpenAPI().specVersion(io.swagger.v3.oas.models.SpecVersion.V31).openapi("3.1.0").info(new Info().title("ArenaOps Login / BFF API").version("1.0.0")
   .description("Code-first reference. Relative server is the current service origin. BFF session cookie, not a bearer token. Obtain the session through the existing browser PKCE flow; Swagger does not collect passwords. See Core specification for delegated resource APIs. Documentation access is separate from API authorization. Keycloak-native email verification/password setup is not an ArenaOps REST API. The global /error dispatch and implicit HEAD/OPTIONS are described in docs/api/README.md."))
   .components(components).servers(List.of(new Server().url("/").description("Current service origin")));
  var catalog=new LinkedHashMap<String,Object>();
  for(var code:ErrorCode.values())catalog.put(code.name(),Map.of("status",code.getStatus(),"description",code.getDescription()));
  api.addExtension("x-error-catalog",catalog);
  api.addExtension("x-routing-errors",Map.of("405","METHOD_NOT_ALLOWED: unsupported method on an existing route","406","NOT_ACCEPTABLE: unsupported Accept format","404","RESOURCE_NOT_FOUND: no matching handler/resource; security may reject first"));
  return api;
 }
 @Bean OperationCustomizer documentedOperations(){return (operation,handler)->{
  var method=handler.getMethod();var controller=handler.getBeanType().getSimpleName();
  boolean secured=controller.equals("TokenController")&&method.getName().equals("getUserInfo");
  operation.setSecurity(secured?List.of(new SecurityRequirement().addList("sessionCookie")):new ArrayList<>());
  var errors=new LinkedHashSet<ErrorCode>();
  errors.add(ErrorCode.INTERNAL_SERVER_ERROR);
  if(secured){errors.add(ErrorCode.AUTHENTICATION_REQUIRED);errors.add(ErrorCode.ACCESS_DENIED);}
  boolean body=false,parameters=false,validated=false,rawBytes=false;
  for(var p:method.getParameters()){
   if(p.isAnnotationPresent(org.springframework.web.bind.annotation.RequestBody.class)||p.isAnnotationPresent(RequestPart.class)){body=true;parameters=true;}
   if(p.isAnnotationPresent(jakarta.validation.Valid.class))validated=true;
   if(p.getType()==byte[].class)rawBytes=true;
   if(p.isAnnotationPresent(RequestParam.class)||p.isAnnotationPresent(PathVariable.class)||p.isAnnotationPresent(RequestHeader.class))parameters=true;
  }
  if(parameters)errors.add(ErrorCode.INVALID_REQUEST);
  if(validated)errors.add(ErrorCode.VALIDATION_FAILED);
  if(body&&!rawBytes)errors.add(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
  var documented=method.getAnnotation(DocumentedErrors.class);if(documented!=null)errors.addAll(List.of(documented.value()));
  if(operation.getResponses()==null)operation.setResponses(new ApiResponses());
  for(var code:errors){
   String status=String.valueOf(code.getStatus());var response=operation.getResponses().get(status);
   if(response==null){response=new ApiResponse().description("Request rejected; see named examples for error codes and recovery guidance.");operation.getResponses().addApiResponse(status,response);}
   if(response.getContent()==null)response.setContent(new Content());
   var media=response.getContent().get("application/json");if(media==null){media=new MediaType().schema(new Schema<>().$ref("#/components/schemas/ErrorResponse"));response.getContent().addMediaType("application/json",media);}
   var value=new LinkedHashMap<String,Object>();value.put("timestamp","2026-01-01T00:00:00Z");value.put("status",code.getStatus());value.put("errorCode",code.name());value.put("description",code.getDescription());value.put("path","/api/example");value.put("correlationId","00000000-0000-4000-8000-000000000001");
   if(code==ErrorCode.VALIDATION_FAILED){value.put("reasonCode","INVALID_REQUEST_FIELDS");value.put("fieldErrors",Map.of("field","Review the validation rule for this field."));}
   media.addExamples(code.name(),new Example().summary(code.name()).description(recovery(code.getStatus())).value(value));
  }
  if(operation.getParameters()!=null)for(var parameter:operation.getParameters()){
   if(parameter.getDescription()==null)parameter.setDescription(switch(parameter.getName()){
    case "Idempotency-Key"->"Required academy-scoped UUID; retry uncertain generation with this same key and identical payload.";
    case "academyId"->"Academy ID; must equal the authenticated actor's academy.";
    case "sportId"->"Registered sport ID in the current academy (not a catalogue code).";
    case "id"->"Persistent resource ID, resolved within the authorized tenant.";
    case "customerId"->"Administrative target customer ID; platform administrator authorization required.";
    case "invitationId"->"Administrative invitation ID (not its activation secret).";
    case "search"->"Optional case-insensitive name search; blank means no name filter.";
    case "page"->"Zero-based page index, >=0.";
    case "size"->"Page size, 1..100; controller-specific default shown in schema.";
    case "active"->"Optional saved active-state filter; omit to include both states.";
    case "status"->"Optional batch status filter.";
    default->"Request parameter; validated by the controller and tenant-scoped service.";
   });
   if(parameter.getSchema()!=null&&parameter.getName().equals("page"))parameter.getSchema().setMinimum(java.math.BigDecimal.ZERO);
   if(parameter.getSchema()!=null&&parameter.getName().equals("size")){parameter.getSchema().setMinimum(java.math.BigDecimal.ONE);parameter.getSchema().setMaximum(java.math.BigDecimal.valueOf(100));}
  }
  // Swagger must not expose framework authentication/session objects as client parameters.
  if(operation.getParameters()!=null)operation.getParameters().removeIf(p->Set.of("jwt","user","session","servletRequest","request").contains(p.getName()));
  operation.addExtension("x-handler",handler.getBeanType().getName()+"#"+method.getName());
  return operation;
 };}
 @Bean org.springdoc.core.customizers.OpenApiCustomizer representativeExamples(){return api->{
  if(api.getPaths()==null)return;
  api.getPaths().forEach((path,item)->item.readOperationsMap().forEach((httpMethod,operation)->{
   String handler=operation.getExtensions().get("x-handler").toString();
   operation.setOperationId(handler.substring(handler.lastIndexOf('.')+1).replace('#','_')+"_"+httpMethod.name().toLowerCase()+"_"+path.replaceAll("[^A-Za-z0-9]","_"));
   if(operation.getResponses()!=null)operation.getResponses().forEach((status,response)->{
    if(response.getContent()!=null)response.getContent().values().forEach(media->{if(media.getExamples()!=null)media.getExamples().values().forEach(ex->{if(ex.getValue() instanceof Map value){var copy=new LinkedHashMap<>(value);copy.put("path",path.replaceAll("\\{[^}]+\\}","1"));ex.setValue(copy);}});});
    if(status.startsWith("2")&&response.getContent()!=null&&response.getContent().containsKey("*/*")){
     var media=response.getContent().remove("*/*");
     boolean text=operation.getExtensions().get("x-handler").toString().endsWith("TokenController#health");
     response.getContent().addMediaType(text?"text/plain":"application/json",media);
     if(text)media.setExample("OK");
    }
   });
   if(operation.getRequestBody()!=null&&operation.getRequestBody().getContent()!=null)operation.getRequestBody().getContent().forEach((mime,media)->{if(media.getExample()==null&&mime.equals("application/json"))media.setExample(example(api,media.getSchema(),"request",new HashSet<>(),0));});
   operation.getResponses().forEach((status,response)->{if(response.getContent()!=null)response.getContent().forEach((mime,media)->{if(status.startsWith("2")&&mime.equals("application/json")&&media.getExample()==null)media.setExample(example(api,media.getSchema(),"response",new HashSet<>(),0));});});
  }));
 };}
 @SuppressWarnings("unchecked")
 private static Object example(OpenAPI api,Schema schema,String name,Set<String> seen,int depth){
  if(schema==null||depth>8)return null;
  if(schema.getExample()!=null)return schema.getExample();
  if(schema.get$ref()!=null){String key=schema.get$ref().substring(schema.get$ref().lastIndexOf('/')+1);if(!seen.add(key))return null;var result=example(api,api.getComponents().getSchemas().get(key),name,seen,depth+1);seen.remove(key);return result;}
  if(schema.getEnum()!=null&&!schema.getEnum().isEmpty())return schema.getEnum().get(0);
  if("object".equals(schema.getType())&&schema.getProperties()==null)return new LinkedHashMap<String,Object>();
  if(schema.getProperties()!=null){var result=new LinkedHashMap<String,Object>();((Map<String,Schema>)schema.getProperties()).forEach((field,child)->{if(!Boolean.TRUE.equals(child.getReadOnly()))result.put(field,example(api,child,field,new HashSet<>(seen),depth+1));});return result;}
  if(schema.getItems()!=null){Object item=example(api,schema.getItems(),name,new HashSet<>(seen),depth+1);return item==null?List.of():List.of(item);}
  String type=schema.getType();if(type==null&&schema.getTypes()!=null)type=(String)schema.getTypes().stream().filter(t->!t.equals("null")).findFirst().orElse(null);
  if("boolean".equals(type))return true;
  if("integer".equals(type)||"number".equals(type)){if(name.toLowerCase().contains("rate"))return 400;if(name.equals("version")||name.equals("number")||name.equals("page"))return 0;return schema.getMinimum()!=null?schema.getMinimum().max(java.math.BigDecimal.ONE):1;}
  if("uuid".equals(schema.getFormat()))return "00000000-0000-4000-8000-000000000001";
  if("date".equals(schema.getFormat()))return "2026-01-01";
  if("date-time".equals(schema.getFormat()))return "2026-01-01T00:00:00Z";
  if("email".equals(schema.getFormat())||name.toLowerCase().contains("email"))return "person@example.test";
  if(name.equals("designation"))return "COACH";
  if(name.toLowerCase().contains("phone"))return "+0000000000";
  if(name.equals("sports")||name.equals("catalogueCode"))return "BADMINTON";
  if(name.equals("startTime"))return "09:00:00";if(name.equals("endTime"))return "10:00:00";
  if(schema.getPattern()!=null&&schema.getPattern().matches("[A-Z_|]+"))return schema.getPattern().split("\\|")[0];
  return switch(name){case "password"->"EXAMPLE_ONLY_PASSWORD_NOT_A_CREDENTIAL";case "redirectUri"->"http://localhost:4200/login/callback";case "code"->"C1";case "role"->"OWNER";case "username"->"example.user";case "displayName","name","academy"->"Example Academy";case "timezone"->"Asia/Kolkata";case "codeVerifier"->"EXAMPLE_ONLY_VERIFIER_012345678901234567890123456789";case "activationCode","onboardingCode"->"EXAMPLE_ONLY_NOT_A_VALID_ACTIVATION_CODE";default->"example";};
 }
 private static String recovery(int status){return switch(status){case 400,413,415->"Correct the request; do not retry unchanged.";case 401->"Sign in again.";case 403->"Use an authorized active account; do not change tenant identifiers to bypass access.";case 404->"Check the identifier within your authorized tenant.";case 409->"Preserve the draft, fetch current state and reconcile before retrying; idempotent retries must retain the original key and payload.";default->"Retry only when safe; retain idempotency keys for uncertain creation results. Supply the correlation ID to support.";};}
}
