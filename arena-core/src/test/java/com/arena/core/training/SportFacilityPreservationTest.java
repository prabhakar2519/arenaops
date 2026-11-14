package com.arena.core.training;

import com.arena.core.entity.*;
import com.arena.core.repository.*;
import com.arena.core.service.*;
import com.arena.core.sessions.*;
import com.arena.core.billing.AcademyPayment;
import com.arena.core.facility.*;
import com.arena.core.exception.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.springframework.orm.hibernate5.HibernateTransactionManager;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.junit.jupiter.api.*;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real isolated persistence and service transactions; no external database or tenant data. */
class SportFacilityPreservationTest {
 SessionFactory factory;TransactionTemplate tx;TrainingService training;FacilityService facility;
 AcademyRunningSessionRepository sessions;AcademyAuthorization auth;Long academy,sportId,facilityId,inactiveId;
 final Jwt jwt=Jwt.withTokenValue("isolated").header("alg","none").subject("owner").build();
 @BeforeEach void setup(){
  factory=new Configuration().addAnnotatedClass(CustomerEntity.class).addAnnotatedClass(AcademySport.class).addAnnotatedClass(AcademyPlayArea.class).addAnnotatedClass(AcademyRunningSession.class).addAnnotatedClass(AcademyPayment.class)
   .setProperty("hibernate.connection.driver_class","org.h2.Driver").setProperty("hibernate.connection.url","jdbc:h2:mem:preserve-"+UUID.randomUUID()+";MODE=PostgreSQL").setProperty("hibernate.hbm2ddl.auto","create-drop").setProperty("hibernate.current_session_context_class","org.springframework.orm.hibernate5.SpringSessionContext").buildSessionFactory();
  var manager=new HibernateTransactionManager();manager.setSessionFactory(factory);manager.setAutodetectDataSource(false);manager.afterPropertiesSet();tx=new TransactionTemplate(manager);
  tx.executeWithoutResult(t->{
   var c=CustomerEntity.builder().customerName("Club").parlourName("Club").sports("SINGLE").organizationType("CLUB").onboardingDate(LocalDate.now()).trialStartsAt(LocalDate.now()).trialEndsAt(LocalDate.now().plusDays(14)).subscriptionType("MONTHLY").billingAmount(BigDecimal.ZERO).billingStartDate(LocalDate.now()).nextDueDate(LocalDate.now()).paymentStatus("NOT_DUE").status("ACTIVE").build();factory.getCurrentSession().persist(c);academy=c.getId();
   var s=new AcademySport();s.setAcademyId(academy);s.setName("Snooker");s.setCatalogueCode("SNOOKER");s.setRegistrationKey("SNOOKER");s.setActive(true);s.setSessions(true);factory.getCurrentSession().persist(s);sportId=s.getId();
   facilityId=area("E1",true);inactiveId=area("E2",false);
  });
  var customers=mock(CustomerRepository.class);var sports=mock(AcademySportRepository.class);var areas=mock(AcademyPlayAreaRepository.class);sessions=mock(AcademyRunningSessionRepository.class);auth=mock(AcademyAuthorization.class);
  var actor=new AcademyAuthorization.Actor(AppUserEntity.builder().id(1L).customerId(academy).username("owner").build(),null,Set.of("OWNER"));
  when(auth.manage(jwt)).thenAnswer(i->{lockCustomer();return actor;});when(auth.actor(jwt)).thenAnswer(i->{lockCustomer();return actor;});
  when(customers.findBillingCustomerForUpdate(academy)).thenAnswer(i->Optional.of(lockCustomer()));when(customers.findById(academy)).thenAnswer(i->Optional.of(factory.getCurrentSession().get(CustomerEntity.class,academy)));
  when(sports.findById(anyLong())).thenAnswer(i->Optional.ofNullable(factory.getCurrentSession().get(AcademySport.class,(Long)i.getArgument(0))));
  when(sports.findByAcademyIdOrderByName(academy)).thenAnswer(i->factory.getCurrentSession().createQuery("from AcademySport where academyId=:a",AcademySport.class).setParameter("a",academy).getResultList());
  when(sports.saveAndFlush(any())).thenAnswer(i->{factory.getCurrentSession().flush();return i.getArgument(0);});
  when(areas.findByAcademyIdAndSportIdOrderById(academy,sportId)).thenAnswer(i->factory.getCurrentSession().createQuery("from AcademyPlayArea where academyId=:a and sportId=:s order by id",AcademyPlayArea.class).setParameter("a",academy).setParameter("s",sportId).getResultList());
  when(areas.locked(eq(academy),anyLong())).thenAnswer(i->Optional.ofNullable(factory.getCurrentSession().get(AcademyPlayArea.class,(Long)i.getArgument(1),org.hibernate.LockMode.PESSIMISTIC_WRITE)));
  when(areas.saveAndFlush(any())).thenAnswer(i->{factory.getCurrentSession().flush();return i.getArgument(0);});
  when(sessions.existsByAcademyIdAndSportIdAndStatus(eq(academy),eq(sportId),anyString())).thenAnswer(i->factory.getCurrentSession().createQuery("select count(s) from AcademyRunningSession s where academyId=:a and sportId=:s and status=:state",Long.class).setParameter("a",academy).setParameter("s",sportId).setParameter("state",i.getArgument(2)).getSingleResult()>0);
  var setup=mock(AcademySetupService.class);var audit=mock(AuditService.class);
  training=proxy(new TrainingService(mock(AppUserRepository.class),customers,mock(CustomerAccessService.class),new AdminAuthorizationService(),mock(SportCatalogueRepository.class),sports,mock(AcademyStaffRepository.class),mock(TrainingBatchRepository.class),mock(BatchScheduleRepository.class),audit,auth,setup,areas,sessions),manager);
  facility=proxy(new FacilityService(auth,sports,areas,mock(FacilityLedger.class),audit,new ObjectMapper(),mock(AuditEventRepository.class),setup,customers,sessions),manager);
 }
 CustomerEntity lockCustomer(){return factory.getCurrentSession().get(CustomerEntity.class,academy,org.hibernate.LockMode.PESSIMISTIC_WRITE);}
 @SuppressWarnings("unchecked") <T>T proxy(T raw,HibernateTransactionManager manager){var p=new ProxyFactory(raw);p.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));return (T)p.getProxy();}
 Long area(String code,boolean active){var f=new AcademyPlayArea();f.setAcademyId(academy);f.setSportId(sportId);f.setCode(code);f.setName(code);f.setFacilityType("ENGLISH");f.setKind("TABLE");f.setHourlyRate(new BigDecimal("220.00"));f.setFrameRate(new BigDecimal("80.00"));f.setActive(active);factory.getCurrentSession().persist(f);return f.getId();}
 AcademySport state(){return tx.execute(t->factory.getCurrentSession().get(AcademySport.class,sportId));}
 void active(boolean value){var s=state();training.updateSport(jwt,sportId,new TrainingRequests.SportUpdate(s.getVersion(),s.isTraining(),s.isSessions(),value));}
 Long session(String status,boolean paused){return tx.execute(t->{var s=new AcademyRunningSession();s.setAcademyId(academy);s.setSportId(sportId);s.setFacilityId(facilityId);s.setStatus(status);s.setStartedAt(LocalDateTime.now().minusHours(1));s.setRatePerHour(new BigDecimal("220.00"));s.setStartedBy("owner");if(paused)s.setPausedAt(LocalDateTime.now().minusMinutes(5));if(status.equals("UNCONFIRMED")){s.setTotalAmount(new BigDecimal("57.00"));s.setPaidAmount(new BigDecimal("20.00"));s.setStoppedAt(LocalDateTime.now());}factory.getCurrentSession().persist(s);return s.getId();});}
 @AfterEach void close(){if(factory!=null)factory.close();}
 @Test void deactivationAndReactivationPreserveFacilityIdentityRatesAndIndividualStatus()throws Exception{
  for(String code:List.of("SNOOKER","BADMINTON")){
   tx.executeWithoutResult(t->{var s=factory.getCurrentSession().get(AcademySport.class,sportId);s.setCatalogueCode(code);});
   var before=facility.list(jwt,academy,sportId);active(false);assertEquals(before,facility.list(jwt,academy,sportId));
   assertEquals(ErrorCode.FACILITY_CAPABILITY_DISABLED,assertThrows(FacilityFailure.class,()->facility.patch(jwt,academy,new ObjectMapper().readTree("{\"facilities\":[{\"facilityId\":"+facilityId+",\"version\":0,\"active\":false}]}"))).getErrorCode());
   assertThrows(FacilityFailure.class,()->tx.executeWithoutResult(t->facility.requireEligibleLocked(jwt,academy,facilityId)));
   active(true);assertEquals(before,facility.list(jwt,academy,sportId));
   tx.executeWithoutResult(t->assertEquals(facilityId,facility.requireEligibleLocked(jwt,academy,facilityId).getId()));
   assertThrows(FacilityFailure.class,()->tx.executeWithoutResult(t->facility.requireEligibleLocked(jwt,academy,inactiveId)));
  }
 }
 @Test void runningPausedAndReservedSessionsBlockDeactivationWithoutMutation(){
  for(String status:List.of("STARTED","RESERVED")){for(boolean paused:status.equals("STARTED")?List.of(false,true):List.of(false)){
   Long id=session(status,paused);assertEquals(status.equals("STARTED")?ErrorCode.SPORT_SESSION_ACTIVE:ErrorCode.SPORT_RESERVATION_EXISTS,assertThrows(ArenaOpsException.class,()->active(false)).getErrorCode());assertTrue(state().isActive());
   tx.executeWithoutResult(t->{var s=factory.getCurrentSession().get(AcademyRunningSession.class,id);assertEquals(status,s.getStatus());factory.getCurrentSession().remove(s);});
  }}
 }
 @Test void unsettledBillAndPaymentRemainUnchangedThroughSportToggle(){
  Long id=session("UNCONFIRMED",false);Long payment=tx.execute(t->{var p=new AcademyPayment();p.setAcademyId(academy);p.setSessionId(id);p.setAmount(new BigDecimal("20.00"));p.setPaymentDate(LocalDate.now());p.setMethod("CASH");factory.getCurrentSession().persist(p);return p.getId();});
  active(false);active(true);
  tx.executeWithoutResult(t->{var s=factory.getCurrentSession().get(AcademyRunningSession.class,id);assertEquals("UNCONFIRMED",s.getStatus());assertEquals(new BigDecimal("57.00"),s.getTotalAmount());assertEquals(new BigDecimal("20.00"),s.getPaidAmount());assertEquals(facilityId,s.getFacilityId());assertEquals(new BigDecimal("20.00"),factory.getCurrentSession().get(AcademyPayment.class,payment).getAmount());});
 }
}
