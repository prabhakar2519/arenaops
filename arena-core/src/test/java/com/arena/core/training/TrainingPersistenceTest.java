package com.arena.core.training;

import com.arena.core.entity.*;
import com.arena.core.enums.*;
import com.arena.core.exception.*;
import com.arena.core.repository.*;
import com.arena.core.service.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.*;
import static com.arena.core.training.TrainingRequests.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest @AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Import({com.arena.core.config.DatabaseSchemaConfiguration.class,TrainingService.class,AcademyAuthorization.class,AcademySetupService.class,StaffLoginService.class,AcademyFacilityService.class,AdminAuthorizationService.class,CustomerAccessService.class,AuditService.class})
@TestPropertySource(properties={"spring.datasource.url=${ARENA_BILLING_DB_URL}","spring.datasource.username=${ARENA_BILLING_DB_USER:validation}","spring.datasource.password=${ARENA_BILLING_DB_PASSWORD:validation}","spring.jpa.hibernate.ddl-auto=validate","spring.profiles.active=local","ARENA_DB_SCHEMA=arena_sit","spring.jpa.show-sql=false"})
@EnabledIfEnvironmentVariable(named="ARENA_BILLING_DB_URL",matches=".+")
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class TrainingPersistenceTest {
 @Autowired TrainingService service;
 @Autowired AcademySetupService setupService;
 @Autowired StaffLoginService loginService;
 @Autowired AcademyFacilityService facilityService;
 @Autowired CustomerRepository customers;
 @Autowired CustomerSubscriptionRepository subscriptions;
 @Autowired AppUserRepository users;
 @Autowired AcademySportRepository sports;
 @Autowired AcademyStaffRepository staff;
 @Autowired TrainingBatchRepository batches;
 @Autowired BatchScheduleRepository schedules;
 @Autowired SportCatalogueRepository catalogue;
 @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
 @MockBean KeycloakService identities;
 @MockBean AccountSetupNotification notifications;
 Jwt jwt;CustomerEntity academy;
 @BeforeEach void setup() {
  String name="training-"+UUID.randomUUID();LocalDate today=LocalDate.now();
  academy=customers.saveAndFlush(CustomerEntity.builder().customerName(name).parlourName(name).sports("ACADEMY").organizationType("ACADEMY")
   .onboardingDate(today).trialStartsAt(today).trialEndsAt(today.plusDays(14)).subscriptionType("MONTHLY").billingAmount(BigDecimal.ZERO)
   .billingStartDate(today).nextDueDate(today.plusDays(14)).paymentStatus("NOT_DUE").status("ACTIVE").customerStatus(CustomerStatus.ACTIVE).accessStatus(AccessStatus.ALLOWED).build());
  subscriptions.saveAndFlush(CustomerSubscriptionEntity.builder().customerId(academy.getId()).plan("ACADEMY").subscriptionStatus(SubscriptionStatus.TRIAL).trialDurationDays(14).trialStartedAt(LocalDateTime.now()).trialEndsAt(LocalDateTime.now().plusDays(14)).build());
  users.saveAndFlush(AppUserEntity.builder().username(name).email(name+"@example.test").role("OWNER").passwordHash("unused").customerId(academy.getId()).isActive(true).build());
  jwt=Jwt.withTokenValue("test").header("alg","none").subject(name).claim("preferred_username",name).claim("realm_access",Map.of("roles",List.of("OWNER"))).build();
 }
 private AcademySport sport(String code) {return service.register(jwt,new Sport(code,null,true,false));}
 private AcademyStaff coach(Long sport) {
  String name="coach-"+UUID.randomUUID();var m=service.addStaff(jwt,new Staff(name,name,name+"@example.test",null,"COACH"));
  return service.assignSports(jwt,m.getId(),new AssignSports(m.getVersion(),Set.of(sport)));
 }
 private TrainingBatch draft(Long sport,String code) {return service.saveBatch(jwt,null,new Batch(null,sport,code,code,null,12,LocalDate.parse("2026-01-01"),null,"DRAFT"));}
 private TrainingBatch withCoach(TrainingBatch b,AcademyStaff coach) {return service.assignCoaches(jwt,b.getId(),new Coaches(b.getVersion(),Set.of(coach.getId()),coach.getId()));}
 private TrainingBatch schedule(TrainingBatch b,String start,String end) {
  service.replaceSchedules(jwt,b.getId(),new Schedules(b.getVersion(),List.of(new Weekly(1,LocalTime.parse(start),LocalTime.parse(end),LocalDate.parse("2026-01-01"),null))));
  return batches.findById(b.getId()).orElseThrow();
 }
 private TrainingBatch state(TrainingBatch b,String status) {return service.saveBatch(jwt,b.getId(),new Batch(b.getVersion(),b.getSportId(),b.getName(),b.getCode(),b.getLevel(),b.getCapacity(),b.getStartDate(),b.getEndDate(),status));}
 private void denied(ErrorCode code,Runnable action) {assertEquals(code,assertThrows(ArenaOpsException.class,action::run).getErrorCode());}
 @Test void catalogueSeededAndCustomActivitiesStayLocal() {
  assertEquals(42,catalogue.count());assertTrue(catalogue.findByCodeAndActiveTrue("CRICKET").isPresent());
  var s=service.register(jwt,new Sport("OTHER","  Acro   Fitness ",true,false));assertEquals("Acro Fitness",s.getName());assertNull(s.getCatalogueCode());assertEquals(42,catalogue.count());
  denied(ErrorCode.TRAINING_SPORT_DUPLICATE,()->service.register(jwt,new Sport("OTHER","acro fitness",true,false)));
 }
 @Test void sportsCannotBeDuplicatedEvenAfterDeactivation() {
  var s=sport("BADMINTON");service.updateSport(jwt,s.getId(),new SportUpdate(s.getVersion(),true,false,false));
  denied(ErrorCode.TRAINING_SPORT_DUPLICATE,()->sport("BADMINTON"));
  var inactive=sports.findById(s.getId()).orElseThrow();assertTrue(service.updateSport(jwt,s.getId(),new SportUpdate(inactive.getVersion(),true,true,true)).isSessions());
 }
 @Test void nonAcademyClubCannotEnterTrainingAndItsLegacySportsAreUnchanged() {
  var club=customers.findById(academy.getId()).orElseThrow();club.setOrganizationType("CLUB");club.setSports("SINGLE");customers.saveAndFlush(club);
  assertEquals(false,service.context(jwt).get("eligible"));assertEquals(true,service.context(jwt).get("workspaceEligible"));assertNotNull(sport("SNOOKER"));assertNotNull(service.addStaff(jwt,new Staff(null,"Staff",null,null,"STAFF")));
  assertEquals("SINGLE",customers.findById(academy.getId()).orElseThrow().getSports());
 }
 @Test void realmRoleAndOwnerMembershipAreBothRequired() {
  var missing=Jwt.withTokenValue("test").header("alg","none").subject(jwt.getSubject()).build();
  denied(ErrorCode.ACCESS_DENIED,()->service.context(missing));
  var staffJwt=Jwt.withTokenValue("test").header("alg","none").subject(jwt.getSubject()).claim("realm_access",Map.of("roles",List.of("COACH"))).build();
  denied(ErrorCode.ACCESS_DENIED,()->service.context(staffJwt));
 }
 @Test void crossAcademyIdsCannotBeReadOrAssigned() {
  var s=sport("CRICKET");var m=coach(s.getId());var b=draft(s.getId(),"A");Jwt first=jwt;
  setup();
  denied(ErrorCode.RESOURCE_NOT_FOUND,()->service.detail(jwt,b.getId()));
  denied(ErrorCode.RESOURCE_NOT_FOUND,()->service.updateSport(jwt,s.getId(),new SportUpdate(s.getVersion(),true,false,false)));
  denied(ErrorCode.RESOURCE_NOT_FOUND,()->service.assignSports(jwt,m.getId(),new AssignSports(m.getVersion(),Set.of(s.getId()))));
  var second=sport("CRICKET");var other=draft(second.getId(),"B");
  denied(ErrorCode.RESOURCE_NOT_FOUND,()->service.assignCoaches(jwt,other.getId(),new Coaches(other.getVersion(),Set.of(m.getId()),null)));
  assertNotNull(service.detail(first,b.getId()));
 }
 @Test void draftActivatesOnlyAfterCoachesAndSchedules() {
  var s=sport("CRICKET");var b=draft(s.getId(),"A");denied(ErrorCode.TRAINING_ACTIVATION_INCOMPLETE,()->state(b,"ACTIVE"));
  var with=withCoach(b,coach(s.getId()));denied(ErrorCode.TRAINING_ACTIVATION_INCOMPLETE,()->state(with,"ACTIVE"));
  var ready=schedule(with,"09:00","10:00");assertEquals("ACTIVE",state(ready,"ACTIVE").getStatus());assertEquals(false,service.context(jwt).get("setupComplete"));
 }
 @Test void activeCoachMembershipAndSportAssignmentAreRequired() {
  var s=sport("CRICKET");var b=draft(s.getId(),"A");
  String name="plain-"+UUID.randomUUID();var m=service.addStaff(jwt,new Staff(name,name,name+"@example.test",null,"STAFF"));
  denied(ErrorCode.TRAINING_COACH_INVALID,()->withCoach(b,m));
  var coach=service.updateStaff(jwt,m.getId(),new StaffUpdate(m.getVersion(),name,m.getEmail(),null,"COACH",true));
  denied(ErrorCode.TRAINING_COACH_INVALID,()->withCoach(b,coach));
 }
 @Test void sameCoachConflictAcrossBatchesAndAdjacentSlotsAllowed() {
  var s=sport("CRICKET");var m=coach(s.getId());var a=state(schedule(withCoach(draft(s.getId(),"A"),m),"09:00","10:00"),"ACTIVE");
  var b=withCoach(draft(s.getId(),"B"),m);
  denied(ErrorCode.TRAINING_SCHEDULE_CONFLICT,()->schedule(b,"09:30","10:30"));
  assertEquals("ACTIVE",state(schedule(b,"10:00","11:00"),"ACTIVE").getStatus());
  denied(ErrorCode.TRAINING_DEPENDENCY_EXISTS,()->service.updateStaff(jwt,m.getId(),new StaffUpdate(m.getVersion(),m.getName(),m.getEmail(),null,"COACH",false)));
  assertEquals("ACTIVE",batches.findById(a.getId()).orElseThrow().getStatus());
 }
 @Test void overlappingSchedulesAndForeignLeadCoachAreRejected() {
  var s=sport("CRICKET");var m=coach(s.getId());var b=withCoach(draft(s.getId(),"A"),m);
  var w=new Weekly(1,LocalTime.of(9,0),LocalTime.of(10,0),LocalDate.parse("2026-01-01"),null);
  denied(ErrorCode.TRAINING_SCHEDULE_CONFLICT,()->service.replaceSchedules(jwt,b.getId(),new Schedules(b.getVersion(),List.of(w,w))));
  denied(ErrorCode.TRAINING_COACH_INVALID,()->service.assignCoaches(jwt,b.getId(),new Coaches(b.getVersion(),Set.of(m.getId()),m.getId()+99999)));
 }
 @Test void oldSchedulesAndInactiveBatchesArePreserved() {
  var s=sport("CRICKET");var m=coach(s.getId());var b=schedule(withCoach(draft(s.getId(),"A"),m),"09:00","10:00");
  var changed=schedule(b,"10:00","11:00");
  assertEquals(2,schedules.findAll().stream().filter(v->v.getBatchId().equals(b.getId())).count());
  assertEquals(1,schedules.findByBatchIdAndActiveTrueOrderByDayOfWeekAscStartTimeAsc(b.getId()).size());
  state(changed,"INACTIVE");assertTrue(batches.findById(b.getId()).orElseThrow().getCoachIds().contains(m.getId()));
 }
 @Test void staleWritesAreRejectedAndDependencyChecksProtectSports() {
  var s=sport("CRICKET");var updated=service.updateSport(jwt,s.getId(),new SportUpdate(s.getVersion(),true,false,true));
  denied(ErrorCode.CONFLICT,()->service.updateSport(jwt,s.getId(),new SportUpdate(s.getVersion(),true,false,false)));
  draft(s.getId(),"A");denied(ErrorCode.TRAINING_DEPENDENCY_EXISTS,()->service.updateSport(jwt,s.getId(),new SportUpdate(updated.getVersion(),true,false,false)));
 }
 @Test void ordinaryRegistrationNeverCreatesIdentityOrSendsMail() {
  long count=users.count();TrainingController controller=new TrainingController(service);
  var plain=controller.staff(jwt,new Staff(null,"Coach",null,"9876543210","COACH"));
  var emailed=controller.staff(jwt,new Staff(null,"Staff","person@example.test",null,"STAFF"));
  assertNull(plain.getAppUserId());assertNull(plain.getEmail());assertNull(emailed.getAppUserId());
  assertEquals(count,users.count());verifyNoInteractions(identities);
  denied(ErrorCode.STAFF_LOGIN_ACCESS_UNAVAILABLE,()->controller.invite(jwt,plain.getId()));
 }
 @Test void accountDeliveryFailureCommitsPendingMembershipAndRetryReusesIdentity() {
  String username="login-"+UUID.randomUUID();var m=service.addStaff(jwt,new Staff(null,"Coach",null,null,"COACH"));
  when(identities.provisionAcademyStaff(eq(username),any(),any(),eq(academy.getId()),eq(m.getId()),any())).thenReturn(username);
  doThrow(new ArenaOpsException(ErrorCode.EMAIL_DELIVERY_FAILED)).when(notifications).send(eq(username),anyInt());
  denied(ErrorCode.EMAIL_DELIVERY_FAILED,()->loginService.enable(jwt,m.getId(),new StaffLoginService.Login(m.getVersion(),username,username+"@example.test",Set.of("MANAGER","INSTRUCTOR"))));
  var saved=staff.findById(m.getId()).orElseThrow();assertEquals("PENDING",saved.getLoginState());assertEquals("FAILED",saved.getNotificationState());assertFalse(users.findById(saved.getAppUserId()).orElseThrow().getIsActive());
  doNothing().when(notifications).send(eq(username),anyInt());var retried=loginService.resend(jwt,saved.getId(),new StaffLoginService.Version(saved.getVersion()));assertEquals("SENT",retried.getNotificationState());assertEquals(saved.getAppUserId(),retried.getAppUserId());
  verify(identities,times(1)).provisionAcademyStaff(eq(username),any(),any(),any(),any(),any());
 }
 @Test void legacyFacilityProgressReviewAndRemovalAreDatabaseDerived() {
  var c=customers.findById(academy.getId()).orElseThrow();c.setOwnerName("Owner");c.setOwnerEmail("owner@example.test");c.setOwnerPhone("9876543210");customers.saveAndFlush(c);
  var s=service.register(jwt,new Sport("BADMINTON",null,false,true));var p=setupService.progress(jwt);assertEquals("INCOMPLETE",p.get("status"));
  setupService.confirm(jwt,new AcademySetupService.Confirmation((Long)p.get("version"),true,false));
  // Simulate existing pre-migration legacy data; new operational creation uses the bulk API.
  jdbc.update("INSERT INTO arena_sit.academy_play_area(academy_id,sport_id,name,code,kind,capacity,active,version,created_at,updated_at) VALUES(?,?,?,?,?,?,true,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",academy.getId(),s.getId(),"Court 1","COURT_1","COURT",4);var savedArea=facilityService.list(jwt).get(0);p=setupService.progress(jwt);assertEquals(80L,p.get("percentage"));assertEquals("REVIEW_NEEDED",p.get("status"));
  p=setupService.confirm(jwt,new AcademySetupService.Confirmation((Long)p.get("version"),true,true));assertEquals("READY",p.get("status"));assertEquals(100L,p.get("percentage"));
  facilityService.save(jwt,savedArea.getId(),new AcademyFacilityService.Area(savedArea.getVersion(),s.getId(),savedArea.getName(),savedArea.getCode(),savedArea.getKind(),savedArea.getCapacity(),false));p=setupService.progress(jwt);assertEquals("INCOMPLETE",p.get("status"));assertEquals(false,p.get("reviewConfirmed"));
 }
 @Test void ownerManagedTrainingRequiresScheduleButNoAdditionalCoach() {
  var s=sport("CRICKET");var p=setupService.progress(jwt);setupService.confirm(jwt,new AcademySetupService.Confirmation((Long)p.get("version"),true,false));
  var b=schedule(draft(s.getId(),"OWNER_GROUP"),"09:00","10:00");assertEquals("ACTIVE",state(b,"ACTIVE").getStatus());
 }
 @Test void fullOperationsManagerCanRegisterPersonnelButCannotProvisionLogin() {
  String name="manager-"+UUID.randomUUID();var user=users.saveAndFlush(AppUserEntity.builder().username(name).email(name+"@example.test").role("STAFF").passwordHash("!KEYCLOAK_MANAGED").customerId(academy.getId()).keycloakSubject(name).isActive(true).build());
  var member=service.addStaff(jwt,new Staff(null,"Manager",null,null,"STAFF","MANAGER"));member.setAppUserId(user.getId());member.setKeycloakSubject(name);member.setLoginState("ACTIVE");member.setApplicationRoles(Set.of("MANAGER"));staff.saveAndFlush(member);
  Jwt manager=Jwt.withTokenValue("manager").header("alg","none").subject(name).claim("preferred_username",name).build();
  denied(ErrorCode.ACCESS_DENIED,()->service.addStaff(manager,new Staff(null,"Receptionist",null,null,"STAFF","RECEPTIONIST")));
  member=staff.findById(member.getId()).orElseThrow();member.setManagerAccess("FULL_OPERATIONS");staff.saveAndFlush(member);
  var registered=service.addStaff(manager,new Staff(null,"Receptionist",null,null,"STAFF","RECEPTIONIST"));assertNull(registered.getAppUserId());
  denied(ErrorCode.ACCESS_DENIED,()->loginService.enable(manager,registered.getId(),new StaffLoginService.Login(registered.getVersion(),"other","other@example.test",Set.of("MANAGER"))));
 }
 @Test void instructorReadsOnlyAssignedBatchesAndSports() {
  var s=sport("CRICKET");var coach=coach(s.getId());var own=withCoach(draft(s.getId(),"ASSIGNED"),coach);var other=draft(s.getId(),"UNASSIGNED");
  String name="instructor-"+UUID.randomUUID();var user=users.saveAndFlush(AppUserEntity.builder().username(name).email(name+"@example.test").role("STAFF").passwordHash("!KEYCLOAK_MANAGED").customerId(academy.getId()).keycloakSubject(name).isActive(true).build());
  coach=staff.findById(coach.getId()).orElseThrow();coach.setAppUserId(user.getId());coach.setKeycloakSubject(name);coach.setLoginState("ACTIVE");coach.setApplicationRoles(Set.of("INSTRUCTOR"));staff.saveAndFlush(coach);
  Jwt instructor=Jwt.withTokenValue("instructor").header("alg","none").subject(name).claim("preferred_username",name).build();
  assertEquals(List.of(own.getId()),service.batches(instructor,"",null,null,0,20).getContent().stream().map(TrainingBatch::getId).toList());assertNotNull(service.detail(instructor,own.getId()));denied(ErrorCode.ACCESS_DENIED,()->service.detail(instructor,other.getId()));denied(ErrorCode.ACCESS_DENIED,()->service.addStaff(instructor,new Staff(null,"Staff",null,null,"STAFF")));
  assertEquals(List.of(s.getId()),service.sports(instructor,"",true,0,20).getContent().stream().map(AcademySport::getId).toList());
 }
 @Test void uniqueOwnerConstraintRejectsSecondActiveOwner() {
  String name="second-"+UUID.randomUUID();assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->users.saveAndFlush(AppUserEntity.builder().username(name).email(name+"@example.test").role("OWNER").passwordHash("unused").customerId(academy.getId()).isActive(true).build()));
 }
 @Test void databaseForeignKeysRejectCrossAcademyAssignments() {
  var own=sport("CRICKET");var m=coach(own.getId());Long firstAcademy=academy.getId();setup();var other=sport("BADMINTON");
  assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("INSERT INTO arena_sit.academy_staff_sport(staff_id,academy_id,sport_id) VALUES(?,?,?)",m.getId(),firstAcademy,other.getId()));
 }
 @Test void settingsValidateTimezoneAndRejectStaleVersion() {
  var context=service.context(jwt);Long v=((Number)context.get("version")).longValue();
  denied(ErrorCode.INVALID_REQUEST,()->service.settings(jwt,new Settings(v,"Unknown/Timezone",false)));
  var updated=service.settings(jwt,new Settings(v,"Europe/London",true));assertEquals("Europe/London",updated.get("timezone"));assertEquals(true,updated.get("skipped"));
  denied(ErrorCode.CONFLICT,()->service.settings(jwt,new Settings(v,"Asia/Kolkata",false)));
 }
 @Test void paginationAndFilteringAreAcademyScoped() {
  sport("CRICKET");sport("BADMINTON");
  assertEquals(2,service.sports(jwt,"",null,0,1).getTotalPages());assertEquals(1,service.sports(jwt,"bad",true,0,20).getTotalElements());
  denied(ErrorCode.INVALID_REQUEST,()->service.sports(jwt,"",null,0,101));
 }
 @Test void concurrentConflictingScheduleWritesAreSerialized() throws Exception {
  var s=sport("CRICKET");var m=coach(s.getId());var a=schedule(withCoach(draft(s.getId(),"A"),m),"09:00","10:00");var b=schedule(withCoach(draft(s.getId(),"B"),m),"09:00","10:00");
  ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
  try {
   java.util.function.Function<TrainingBatch,Callable<String>> activate=batch->()->{start.await();try{return state(batch,"ACTIVE").getStatus();}catch(ArenaOpsException ex){return ex.getErrorCode().name();}};
   Future<String> first=pool.submit(activate.apply(a)),second=pool.submit(activate.apply(b));start.countDown();
   assertEquals(Set.of("ACTIVE","TRAINING_SCHEDULE_CONFLICT"),Set.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS)));
  }finally{pool.shutdownNow();}
 }
}
