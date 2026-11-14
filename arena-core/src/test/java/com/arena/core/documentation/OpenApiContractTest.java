package com.arena.core.documentation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.context.annotation.Import;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.core.util.Yaml31;
import io.swagger.v3.core.util.Json31;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Documentation-only MVC context: all business services mocked, no HTTP listener, database or Keycloak. */
@SpringBootTest(classes=OpenApiContractTest.DocumentationOnly.class, webEnvironment=SpringBootTest.WebEnvironment.MOCK,
 properties={"management.endpoint.health.group.readiness.include=ping","springdoc.api-docs.enabled=true","springdoc.swagger-ui.enabled=true","springdoc.api-docs.version=OPENAPI_3_1","springdoc.override-with-generic-response=false","springdoc.writer-with-order-by-keys=true","springdoc.cache.disabled=true"})
@org.springframework.test.context.ActiveProfiles("openapi-contract")
@AutoConfigureMockMvc(addFilters=false)
class OpenApiContractTest {
 @org.springframework.context.annotation.Profile("openapi-contract")
 @org.springframework.context.annotation.Configuration(proxyBeanMethods=false)
 @EnableAutoConfiguration(excludeName={"org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration","org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration","org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration","org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration","org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration","org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration","org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration","org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration"})
 @Import({OpenApiConfiguration.class,com.arena.core.facility.FacilityController.class,com.arena.core.controller.AdminCustomerController.class,com.arena.core.controller.AccessController.class,com.arena.core.controller.AppUserController.class,com.arena.core.controller.HomeController.class,com.arena.core.controller.RegistrationController.class,com.arena.core.profile.BusinessProfileController.class,com.arena.core.billing.BillingController.class,com.arena.core.billing.AcademyBillingController.class,com.arena.core.students.AcademyStudentController.class,com.arena.core.sessions.RunningSessionController.class,com.arena.core.sessions.SessionCheckoutController.class,com.arena.core.checkin.FacilityCheckinController.class,com.arena.core.publicclub.PublicClubController.class,com.arena.core.training.AttendanceController.class,com.arena.core.training.TrainingOperationsController.class,com.arena.core.training.StaffLoginController.class,com.arena.core.training.StaffRegistrationController.class,com.arena.core.training.AcademySetupController.class,com.arena.core.training.TrainingController.class})
 static class DocumentationOnly {}
 @org.springframework.test.context.bean.override.mockito.MockitoBean com.arena.core.training.AcademyAuthorization mockAcademyAuthorization;
 @org.springframework.test.context.bean.override.mockito.MockitoBean com.arena.core.training.AcademyFacilityService mockAcademyFacilityService;
 @org.springframework.test.context.bean.override.mockito.MockitoBean com.arena.core.training.AcademySetupService mockAcademySetupService;
 @org.springframework.test.context.bean.override.mockito.MockitoBean com.arena.core.service.AdminAuthorizationService mockAdminAuthorizationService;
 @org.springframework.test.context.bean.override.mockito.MockitoBean com.arena.core.service.AdminCustomerService mockAdminCustomerService;
 @org.springframework.test.context.bean.override.mockito.MockitoBean com.arena.core.repository.AppUserRepository mockAppUserRepository;
 @org.springframework.test.context.bean.override.mockito.MockitoBean com.arena.core.service.AppUserService mockAppUserService;
 @org.springframework.test.context.bean.override.mockito.MockitoBean com.arena.core.billing.BillingService mockBillingService;
 @org.springframework.test.context.bean.override.mockito.MockitoBean com.arena.core.billing.AcademyBillingService mockAcademyBillingService;
 @org.springframework.test.context.bean.override.mockito.MockitoBean com.arena.core.students.AcademyStudentService mockAcademyStudentService;
 @org.springframework.test.context.bean.override.mockito.MockitoBean com.arena.core.training.AttendanceService mockAttendanceService;
 @org.springframework.test.context.bean.override.mockito.MockitoBean com.arena.core.training.TrainingOperationsService mockTrainingOperationsService;
 @org.springframework.test.context.bean.override.mockito.MockitoBean com.arena.core.sessions.RunningSessionService mockRunningSessionService;
 @org.springframework.test.context.bean.override.mockito.MockitoBean com.arena.core.sessions.SessionCheckoutService mockSessionCheckoutService;
 @org.springframework.test.context.bean.override.mockito.MockitoBean com.arena.core.checkin.FacilityCheckinService mockCheckinService;
 @org.springframework.test.context.bean.override.mockito.MockitoBean com.arena.core.checkin.PermanentCheckinService mockPermanentCheckins;
 @org.springframework.test.context.bean.override.mockito.MockitoBean com.arena.core.publicclub.PublicClubService mockPublicClubs;
 @org.springframework.test.context.bean.override.mockito.MockitoBean com.arena.core.publicclub.PublicClubHost mockPublicHost;
 @org.springframework.test.context.bean.override.mockito.MockitoBean com.arena.core.publicclub.PublicReservationService mockPublicReservations;
 @org.springframework.test.context.bean.override.mockito.MockitoBean com.arena.core.publicclub.ClubCustomerService mockClubCustomers;
 @org.springframework.test.context.bean.override.mockito.MockitoBean com.arena.core.profile.BusinessProfileService mockBusinessProfileService;
 @org.springframework.test.context.bean.override.mockito.MockitoBean com.arena.core.service.CustomerAccessService mockCustomerAccessService;
 @org.springframework.test.context.bean.override.mockito.MockitoBean com.arena.core.validation.CustomerEmailValidator mockCustomerEmailValidator;
 @org.springframework.test.context.bean.override.mockito.MockitoBean com.arena.core.facility.FacilityService mockFacilityService;
 @org.springframework.test.context.bean.override.mockito.MockitoBean com.arena.core.training.StaffLoginService mockStaffLoginService;
 @org.springframework.test.context.bean.override.mockito.MockitoBean com.arena.core.training.TrainingService mockTrainingService;
 @org.springframework.test.context.bean.override.mockito.MockitoBean com.arena.core.training.StaffRegistrationService mockStaffRegistrations;
 @Autowired MockMvc mvc;
 @Autowired @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings;
 @Test void swaggerUiAssetsAndYamlAreServedWithoutProxying()throws Exception{
  mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
  mvc.perform(get("/swagger-ui/swagger-ui.css")).andExpect(status().isOk());
  mvc.perform(get("/swagger-ui/swagger-initializer.js")).andExpect(status().isOk());
  mvc.perform(get("/v3/api-docs/swagger-config")).andExpect(status().isOk());
  mvc.perform(get("/v3/api-docs.yaml")).andExpect(status().isOk());
 }
 @Test void exportAndVerifyEveryMappedOperation()throws Exception{
  String json=mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  var tree=Json31.mapper().readTree(json);assertEquals("3.1.0",tree.path("openapi").asText());
  var inventory=new TreeSet<String>();
  mappings.getHandlerMethods().forEach((mapping,handler)->{
   if(!handler.getBeanType().getPackageName().startsWith("com.arena.core")||handler.hasMethodAnnotation(Hidden.class))return;
   assertNotNull(handler.getMethodAnnotation(Operation.class),handler.toString());
   for(String path:mapping.getPatternValues())for(var verb:mapping.getMethodsCondition().getMethods()){
    var operation=tree.path("paths").path(path).path(verb.name().toLowerCase());assertFalse(operation.isMissingNode(),verb+" "+path);
    assertTrue(operation.hasNonNull("summary"));assertTrue(operation.hasNonNull("description"));assertTrue(operation.has("security"));assertTrue(operation.path("responses").has("500"));
    inventory.add(verb+" "+path);
   }
  });
  assertFalse(inventory.isEmpty());
  Path target=Path.of("target/openapi");Files.createDirectories(target);
  // Parse and re-emit the Springdoc response so export has stable ordering and no host-specific server.
  var spec=Json31.mapper().readValue(json,io.swagger.v3.oas.models.OpenAPI.class);
  Files.writeString(target.resolve("arenaops-core-openapi.yaml"),Yaml31.mapper().writeValueAsString(spec));
  Files.writeString(target.resolve("inventory.txt"),String.join("\n",inventory)+"\n");
  var sourceHashes=new TreeMap<String,String>();
  try(var files=Files.walk(Path.of("src/main"))){for(var file:files.filter(Files::isRegularFile).filter(f->f.toString().endsWith(".java")||f.toString().endsWith(".yaml")).filter(f->!java.util.Set.of("application-dev.yaml","application-mail-local.yaml").contains(f.getFileName().toString())).toList()){
   String digest=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
   sourceHashes.put("arena-core/"+file.toString().replace('\\','/'),digest);
  }}
  if(!Boolean.getBoolean("arena.openapi.update")){
   var reviewed=Json31.mapper().readTree(Files.readString(Path.of("../docs/api/core-source-review.json")));
   assertEquals(reviewed,Json31.mapper().valueToTree(sourceHashes),"Semantic source changed; review documentation and update source-review manifest");
  }

  Path approved=Path.of("../docs/api/arenaops-core-openapi.yaml");
  if(Boolean.getBoolean("arena.openapi.update"))Files.copy(target.resolve("arenaops-core-openapi.yaml"),approved,StandardCopyOption.REPLACE_EXISTING);
  else {assertTrue(Files.exists(approved),"Run the approved export command to create the snapshot");assertEquals(Files.readString(approved),Files.readString(target.resolve("arenaops-core-openapi.yaml")),"OpenAPI drift: regenerate and review the specification diff");}
 }
}
