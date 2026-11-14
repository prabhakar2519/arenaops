package com.arena.core.training;
import com.arena.core.entity.*;
import com.arena.core.exception.*;
import com.arena.core.repository.*;
import com.arena.core.service.*;
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
import static com.arena.core.training.TrainingRequests.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class ActivityRegistrationTransactionTest {
 SessionFactory factory;TransactionTemplate tx;TrainingService service;AcademySportRepository sports;AcademyAuthorization auth;Long academy;
 final Jwt jwt=Jwt.withTokenValue("isolated").header("alg","none").subject("owner").build();
 @BeforeEach void setup(){
  factory=new Configuration().addAnnotatedClass(CustomerEntity.class).addAnnotatedClass(AcademySport.class).setProperty("hibernate.connection.driver_class","org.h2.Driver").setProperty("hibernate.connection.url","jdbc:h2:mem:activities-"+UUID.randomUUID()+";MODE=PostgreSQL").setProperty("hibernate.hbm2ddl.auto","create-drop").setProperty("hibernate.current_session_context_class","org.springframework.orm.hibernate5.SpringSessionContext").buildSessionFactory();
  var manager=new HibernateTransactionManager();manager.setSessionFactory(factory);manager.setAutodetectDataSource(false);manager.afterPropertiesSet();tx=new TransactionTemplate(manager);
  tx.executeWithoutResult(status->{var c=CustomerEntity.builder().customerName("Club").parlourName("Club").sports("SNOOKER").organizationType("CLUB").onboardingDate(LocalDate.now()).trialStartsAt(LocalDate.now()).trialEndsAt(LocalDate.now().plusDays(14)).subscriptionType("MONTHLY").billingAmount(BigDecimal.ZERO).billingStartDate(LocalDate.now()).nextDueDate(LocalDate.now()).paymentStatus("NOT_DUE").status("ACTIVE").build();factory.getCurrentSession().persist(c);factory.getCurrentSession().flush();academy=c.getId();});
  var customers=mock(CustomerRepository.class);sports=mock(AcademySportRepository.class);auth=mock(AcademyAuthorization.class);when(auth.manage(jwt)).thenReturn(new AcademyAuthorization.Actor(AppUserEntity.builder().id(1L).customerId(academy).build(),null,Set.of("OWNER")));
  when(customers.findBillingCustomerForUpdate(academy)).thenAnswer(i->Optional.of(factory.getCurrentSession().get(CustomerEntity.class,academy,org.hibernate.LockMode.PESSIMISTIC_WRITE)));
  when(sports.findByAcademyIdOrderByName(academy)).thenAnswer(i->factory.getCurrentSession().createQuery("from AcademySport where academyId=:academy order by name",AcademySport.class).setParameter("academy",academy).getResultList());
  when(sports.findById(anyLong())).thenAnswer(i->Optional.ofNullable(factory.getCurrentSession().get(AcademySport.class,(Long)i.getArgument(0))));
  when(customers.findById(academy)).thenAnswer(i->Optional.ofNullable(factory.getCurrentSession().get(CustomerEntity.class,academy)));
  when(sports.saveAndFlush(any())).thenAnswer(i->{var value=(AcademySport)i.getArgument(0);factory.getCurrentSession().persist(value);factory.getCurrentSession().flush();return value;});
  var raw=new TrainingService(mock(AppUserRepository.class),customers,mock(CustomerAccessService.class),new AdminAuthorizationService(),mock(SportCatalogueRepository.class),sports,mock(AcademyStaffRepository.class),mock(TrainingBatchRepository.class),mock(BatchScheduleRepository.class),mock(AuditService.class),auth,mock(AcademySetupService.class),mock(AcademyPlayAreaRepository.class));var proxy=new ProxyFactory(raw);proxy.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));service=(TrainingService)proxy.getProxy();
 }
 @AfterEach void close(){if(factory!=null)factory.close();}
 Activities selection(){return new Activities(List.of(new Activity("OTHER","Custom First"),new Activity("OTHER","Custom Second")));}
 long count(){return tx.execute(s->factory.getCurrentSession().createQuery("select count(s) from AcademySport s",Long.class).getSingleResult());}
 @Test void failureAfterFirstFlushRollsBackEntireSelection(){var saves=new java.util.concurrent.atomic.AtomicInteger();doAnswer(i->{var item=(AcademySport)i.getArgument(0);factory.getCurrentSession().persist(item);factory.getCurrentSession().flush();if(saves.incrementAndGet()==2)throw new IllegalStateException("isolated persistence failure");return item;}).when(sports).saveAndFlush(any());assertThrows(IllegalStateException.class,()->service.registerActivities(jwt,selection()));assertEquals(0,count());}
 @Test void invalidSelectionAndRetryAreAllOrNothing(){assertThrows(ActivitySelectionException.class,()->service.registerActivities(jwt,new Activities(List.of(new Activity("OTHER","Custom First"),new Activity("OTHER","x")))));assertEquals(0,count());assertEquals(2,service.registerActivities(jwt,selection()).size());assertEquals(2,count());assertThrows(ActivitySelectionException.class,()->service.registerActivities(jwt,selection()));assertEquals(2,count());}
 @Test void concurrentDuplicateSubmissionsProduceOneRegistrationSet()throws Exception{var pool=java.util.concurrent.Executors.newFixedThreadPool(2);var ready=new java.util.concurrent.CountDownLatch(2);var start=new java.util.concurrent.CountDownLatch(1);java.util.concurrent.Callable<Boolean> task=()->{ready.countDown();start.await(5,java.util.concurrent.TimeUnit.SECONDS);try{service.registerActivities(jwt,selection());return true;}catch(ActivitySelectionException duplicate){return false;}};try{var a=pool.submit(task);var b=pool.submit(task);assertTrue(ready.await(5,java.util.concurrent.TimeUnit.SECONDS));start.countDown();assertEquals(1,(a.get(10,java.util.concurrent.TimeUnit.SECONDS)?1:0)+(b.get(10,java.util.concurrent.TimeUnit.SECONDS)?1:0));assertEquals(2,count());}finally{start.countDown();pool.shutdownNow();}}

 void single(){tx.executeWithoutResult(t->factory.getCurrentSession().get(CustomerEntity.class,academy).setSports("SINGLE"));}
 Activities one(String name,Boolean active){return new Activities(List.of(new Activity("OTHER",name,active)));}
 long activeCount(){return tx.execute(t->factory.getCurrentSession().createQuery("select count(s) from AcademySport s where active=true",Long.class).getSingleResult());}
 @Test void singleSportInitialSubsequentBulkAndInactiveRegistration(){
  single();var first=service.register(jwt,new Sport("OTHER","First Sport",false,true));assertTrue(first.isActive());
  assertEquals(ErrorCode.SINGLE_SPORT_LIMIT_EXCEEDED,assertThrows(ArenaOpsException.class,()->service.register(jwt,new Sport("OTHER","Second Sport",false,true))).getErrorCode());
  assertEquals(ErrorCode.SINGLE_SPORT_LIMIT_EXCEEDED,assertThrows(ArenaOpsException.class,()->service.registerActivities(jwt,selection())).getErrorCode());
  assertEquals(1,count());assertEquals(1,activeCount());
  assertFalse(service.register(jwt,new Sport("OTHER","Inactive Sport",false,true,false)).isActive());
  assertFalse(service.registerActivities(jwt,one("Another Inactive",false)).get(0).isActive());assertEquals(3,count());assertEquals(1,activeCount());
 }
 @Test void emptySingleClubRejectsMultiActiveBulkWithoutPartialWrites(){single();assertEquals(ErrorCode.SINGLE_SPORT_LIMIT_EXCEEDED,assertThrows(ArenaOpsException.class,()->service.registerActivities(jwt,selection())).getErrorCode());assertEquals(0,count());}
 @Test void switchingAndReactivationRespectSingleLimit(){
  single();var a=service.registerActivities(jwt,one("First",true)).get(0);var b=service.registerActivities(jwt,one("Second",false)).get(0);
  assertThrows(ArenaOpsException.class,()->service.updateSport(jwt,b.getId(),new SportUpdate(b.getVersion(),false,false,true)));
  a=service.updateSport(jwt,a.getId(),new SportUpdate(a.getVersion(),false,false,false));
  service.updateSport(jwt,b.getId(),new SportUpdate(b.getVersion(),false,false,true));
  final var previous=a;assertEquals(ErrorCode.SINGLE_SPORT_LIMIT_EXCEEDED,assertThrows(ArenaOpsException.class,()->service.updateSport(jwt,previous.getId(),new SportUpdate(previous.getVersion(),false,false,true))).getErrorCode());assertEquals(1,activeCount());
 }
 @Test void multiSportPlanRetainsSeveralActiveSports(){tx.executeWithoutResult(t->factory.getCurrentSession().get(CustomerEntity.class,academy).setSports("MULTI_SPORTS"));assertEquals(2,service.registerActivities(jwt,selection()).size());assertTrue(service.register(jwt,new Sport("OTHER","Third",false,false)).isActive());assertEquals(3,activeCount());}
 @Test void concurrentDifferentRegistrationsCannotBypassSingleLimit()throws Exception{
  single();race(()->service.registerActivities(jwt,one("First",true)),()->service.registerActivities(jwt,one("Second",true)));assertEquals(1,count());assertEquals(1,activeCount());
 }
 @Test void concurrentReactivationsCannotBypassSingleLimit()throws Exception{
  single();var a=service.registerActivities(jwt,one("First",false)).get(0);var b=service.registerActivities(jwt,one("Second",false)).get(0);
  race(()->service.updateSport(jwt,a.getId(),new SportUpdate(a.getVersion(),false,false,true)),()->service.updateSport(jwt,b.getId(),new SportUpdate(b.getVersion(),false,false,true)));assertEquals(2,count());assertEquals(1,activeCount());
 }
 void race(Runnable first,Runnable second)throws Exception{
  var pool=java.util.concurrent.Executors.newFixedThreadPool(2);var ready=new java.util.concurrent.CountDownLatch(2);var go=new java.util.concurrent.CountDownLatch(1);
  java.util.function.Function<Runnable,java.util.concurrent.Callable<Boolean>> task=action->()->{ready.countDown();go.await(5,java.util.concurrent.TimeUnit.SECONDS);try{action.run();return true;}catch(ArenaOpsException ex){assertEquals(ErrorCode.SINGLE_SPORT_LIMIT_EXCEEDED,ex.getErrorCode());return false;}};
  try{var a=pool.submit(task.apply(first));var b=pool.submit(task.apply(second));assertTrue(ready.await(5,java.util.concurrent.TimeUnit.SECONDS));go.countDown();assertEquals(1,(a.get(10,java.util.concurrent.TimeUnit.SECONDS)?1:0)+(b.get(10,java.util.concurrent.TimeUnit.SECONDS)?1:0));}finally{go.countDown();pool.shutdownNow();}
 }

 @Test void directApiUsesStandardLimitErrorValidatesRequestsAndRejectsUnauthorized()throws Exception{
  single();var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new TrainingController(service)).setControllerAdvice(new GlobalExceptionHandler()).setCustomArgumentResolvers(new org.springframework.web.method.support.HandlerMethodArgumentResolver(){public boolean supportsParameter(org.springframework.core.MethodParameter p){return p.getParameterType()==Jwt.class;}public Object resolveArgument(org.springframework.core.MethodParameter p,org.springframework.web.method.support.ModelAndViewContainer m,org.springframework.web.context.request.NativeWebRequest r,org.springframework.web.bind.support.WebDataBinderFactory b){return jwt;}}).build();
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/academy/sports").contentType("application/json").content("{\"catalogueCode\":\"OTHER\",\"customName\":\"First Sport\",\"sessions\":true}")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated());
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/academy/sports/bulk").contentType("application/json").content("{\"activities\":[{\"catalogueCode\":\"OTHER\",\"customName\":\"Second Sport\"}]}")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.errorCode").value("SINGLE_SPORT_LIMIT_EXCEEDED")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.description").value(ErrorCode.SINGLE_SPORT_LIMIT_EXCEEDED.getDescription()));
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/academy/sports/bulk").contentType("application/json").content("{\"activities\":[]}")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
  when(auth.manage(jwt)).thenThrow(new ArenaOpsException(ErrorCode.ACCESS_DENIED));
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/academy/sports/bulk").contentType("application/json").content("{\"activities\":[{\"catalogueCode\":\"OTHER\",\"customName\":\"Second Sport\",\"active\":false}]}")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());assertEquals(1,count());
 }

 @Test void singleClubCanRegisterOneActiveAndSeveralInactiveActivitiesAtomically(){single();var result=service.registerActivities(jwt,new Activities(List.of(new Activity("OTHER","First",true),new Activity("OTHER","Second",false),new Activity("OTHER","Third",false))));assertEquals(3,result.size());assertEquals(3,count());assertEquals(1,activeCount());}
}
