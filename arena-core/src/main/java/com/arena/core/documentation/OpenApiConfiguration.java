package com.arena.core.documentation;
import com.arena.core.exception.*;
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
  var components=new Components().addSecuritySchemes("bearerJwt",new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT"));
  var resolved=ModelConverters.getInstance(false).resolveAsResolvedSchema(new AnnotatedType(ErrorResponse.class).resolveAsRef(true));
  resolved.referencedSchemas.forEach(components::addSchemas);
  ModelConverters.getInstance(false).resolveAsResolvedSchema(new AnnotatedType(FacilityDocumentation.Failure.class).resolveAsRef(true)).referencedSchemas.forEach(components::addSchemas);
  var api=new OpenAPI().specVersion(io.swagger.v3.oas.models.SpecVersion.V31).openapi("3.1.0").info(new Info().title("ArenaOps Core API").version("1.0.0")
   .description("Code-first reference. Relative server is the current service origin. Protected APIs require a Keycloak Bearer JWT and current tenant membership. Documentation access is separate from API authorization. Keycloak-native email verification/password setup is not an ArenaOps REST API. The global /error dispatch and implicit HEAD/OPTIONS are described in docs/api/README.md."))
   .components(components).servers(List.of(new Server().url("/").description("Current service origin")));
  var catalog=new LinkedHashMap<String,Object>();
  for(var code:ErrorCode.values())catalog.put(code.name(),Map.of("status",code.getStatus(),"description",code.getDescription()));
  api.addExtension("x-error-catalog",catalog);
  api.addExtension("x-routing-errors",Map.of("405","METHOD_NOT_ALLOWED: unsupported method on an existing route","406","NOT_ACCEPTABLE: unsupported Accept format","404","RESOURCE_NOT_FOUND: no matching handler/resource; security may reject first"));
  return api;
 }
 @Bean OperationCustomizer documentedOperations(){return (operation,handler)->{
  var method=handler.getMethod();var controller=handler.getBeanType().getSimpleName();
  boolean secured=!(controller.equals("PublicClubController")&&Set.of("site","logo","request").contains(method.getName()))&&!(controller.equals("FacilityCheckinController")&&Set.of("inspect","submit").contains(method.getName()))&&!(controller.equals("AppUserController")||controller.equals("RegistrationController")||(controller.equals("HomeController")&&method.getName().equals("health")));
  operation.setSecurity(secured?List.of(new SecurityRequirement().addList("bearerJwt")):new ArrayList<>());
  var errors=new LinkedHashSet<ErrorCode>();
  errors.add(ErrorCode.INTERNAL_SERVER_ERROR);
  errors.add(ErrorCode.AUTHENTICATION_REQUIRED); // Even public routes reject an invalid optional Bearer token in the resource-server filter.
  if(secured){errors.add(ErrorCode.ACCESS_DENIED);}
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
   if(controller.equals("FacilityController")&&code.name().startsWith("FACILITY_")){
    String reason=switch(code){case FACILITY_VERSION_CONFLICT->"STALE_VERSION";case FACILITY_INVALID_REQUEST->"INVALID_FIELDS";case FACILITY_IDEMPOTENCY_CONFLICT->"IDEMPOTENCY_PAYLOAD_CHANGED";case FACILITY_CAPABILITY_DISABLED->"CAPABILITY_DISABLED";case FACILITY_UNSUPPORTED->method.getName().equals("create")?"UNSUPPORTED_TYPE":"UNSUPPORTED_SPORT";case FACILITY_NOT_FOUND->method.getName().equals("patch")?"FACILITY_NOT_FOUND":"SPORT_NOT_FOUND";default->"CONCURRENT_CHANGE";};
    value.put("reasonCode",reason);if(code!=ErrorCode.FACILITY_CONCURRENCY_FAILURE){value.put("fieldErrors",Map.of());value.put("conflicts",List.of());}
   }
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
  if(controller.equals("FacilityController")){
   var failure=ModelConverters.getInstance(false).resolveAsResolvedSchema(new AnnotatedType(FacilityDocumentation.Failure.class).resolveAsRef(true));
   // Additional schemas are registered by the OpenAPI bean, not by request processing.
   for(String status:List.of("400","404","409")){
    var response=operation.getResponses().get(status);if(response!=null&&response.getContent()!=null){var media=response.getContent().get("application/json");if(media!=null)media.setSchema(new ComposedSchema().anyOf(List.of(new Schema<>().$ref("#/components/schemas/ErrorResponse"),new Schema<>().$ref("#/components/schemas/FacilityError"))));}
   }
   var response=operation.getResponses().get("409");
   if(response!=null&&method.getName().equals("patch")){
    var code=ErrorCode.FACILITY_VERSION_CONFLICT;response.getContent().get("application/json").addExamples(code.name(),new Example().description("Preserve drafts, fetch current values and reconcile versions before retrying.").value(Map.of("timestamp","2026-01-01T00:00:00Z","status",409,"errorCode",code.name(),"reasonCode","STALE_VERSION","description",code.getDescription(),"path","/api/v1/academies/10/facilities/bulk","correlationId","00000000-0000-4000-8000-000000000001","fieldErrors",Map.of(),"conflicts",List.of(Map.of("facilityId",103,"code","E3","submittedVersion",2,"currentVersion",3)))));
   }
  }
  if(controller.equals("TrainingController")&&method.getName().equals("invite")){operation.getResponses().remove("200");operation.addExtension("x-disabled-operation","Service always rejects with STAFF_LOGIN_ACCESS_UNAVAILABLE after validating resource ownership; no successful invitation delivery.");}
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
   if(path.equals("/api/business-profile")&&operation.getRequestBody()!=null&&operation.getRequestBody().getContent()!=null){
    var multipart=operation.getRequestBody().getContent().get("multipart/form-data");if(multipart!=null)multipart.addEncoding("profile",new Encoding().contentType("application/json"));
   }
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
