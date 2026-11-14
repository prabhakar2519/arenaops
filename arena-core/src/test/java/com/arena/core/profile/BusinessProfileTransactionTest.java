package com.arena.core.profile;
import com.arena.core.entity.*;
import com.arena.core.repository.*;
import com.arena.core.service.*;
import com.arena.core.training.*;
import com.arena.core.exception.*;
import com.arena.core.enums.*;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.springframework.orm.hibernate5.HibernateTransactionManager;
import org.springframework.transaction.interceptor.*;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.mock.web.MockMultipartFile;
import org.junit.jupiter.api.*;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
import java.io.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class BusinessProfileTransactionTest {
 SessionFactory factory;TransactionTemplate tx;BusinessProfileService service;AuditService audit;Long id;Long version;
 Jwt jwt=Jwt.withTokenValue("isolated-test").header("alg","none").subject("owner").build();
 @BeforeEach void setup()throws Exception{
  factory=new Configuration().addAnnotatedClass(CustomerEntity.class).addAnnotatedClass(BusinessLogo.class)
   .setProperty("hibernate.connection.driver_class","org.h2.Driver").setProperty("hibernate.connection.url","jdbc:h2:mem:profile-"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
   .setProperty("hibernate.hbm2ddl.auto","create-drop").setProperty("hibernate.current_session_context_class","org.springframework.orm.hibernate5.SpringSessionContext").buildSessionFactory();
  var manager=new HibernateTransactionManager();manager.setSessionFactory(factory);manager.setAutodetectDataSource(false);manager.afterPropertiesSet();tx=new TransactionTemplate(manager);
  tx.executeWithoutResult(status->{var c=CustomerEntity.builder().customerName("Legal name").parlourName("Club").sports("ACADEMY").organizationType("ACADEMY").onboardingDate(LocalDate.now()).trialStartsAt(LocalDate.now()).trialEndsAt(LocalDate.now().plusDays(14)).subscriptionType("MONTHLY").billingAmount(BigDecimal.ZERO).billingStartDate(LocalDate.now()).nextDueDate(LocalDate.now()).paymentStatus("NOT_DUE").status("ACTIVE").logoRevision("original").build();factory.getCurrentSession().persist(c);factory.getCurrentSession().flush();id=c.getId();version=c.getVersion();var logo=new BusinessLogo();logo.setCustomerId(id);logo.setImage(new byte[]{1,2,3});factory.getCurrentSession().persist(logo);});
  var customers=mock(CustomerRepository.class);var logos=mock(BusinessLogoRepository.class);var auth=mock(AcademyAuthorization.class);audit=mock(AuditService.class);
  var actor=new AcademyAuthorization.Actor(AppUserEntity.builder().id(1L).customerId(id).build(),null,Set.of("OWNER"));when(auth.operations(jwt)).thenReturn(actor);when(auth.manage(jwt)).thenReturn(actor);
  when(customers.findBillingCustomerForUpdate(id)).thenAnswer(i->Optional.ofNullable(factory.getCurrentSession().get(CustomerEntity.class,id,org.hibernate.LockMode.PESSIMISTIC_WRITE)));
  when(customers.saveAndFlush(any())).thenAnswer(i->{factory.getCurrentSession().flush();return i.getArgument(0);});
  when(logos.findById(id)).thenAnswer(i->Optional.ofNullable(factory.getCurrentSession().get(BusinessLogo.class,id)));
  when(logos.save(any())).thenAnswer(i->factory.getCurrentSession().merge(i.getArgument(0)));
  doAnswer(i->{var logo=factory.getCurrentSession().get(BusinessLogo.class,id);if(logo!=null)factory.getCurrentSession().remove(logo);return null;}).when(logos).deleteById(id);
  var raw=new BusinessProfileService(auth,customers,logos,new LogoImages(),audit,mock(AppUserRepository.class),new AdminAuthorizationService(),mock(CustomerAccessService.class),mock(AcademyStaffRepository.class));
  var proxy=new ProxyFactory(raw);proxy.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));service=(BusinessProfileService)proxy.getProxy();
 }
 @AfterEach void close(){if(factory!=null)factory.close();}
 ProfileUpdate update(Long v,String logo){return new ProfileUpdate(v,"Public name",null,null,null,null,null,null,null,null,logo);}
 @Test void failedAuditRollsBackFlushedProfileAndLogoReplacement()throws Exception{
  doThrow(new IllegalStateException("audit unavailable")).when(audit).record(any(),any(),any(),any(),any(),any(),any(),any());
  var out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",out);
  assertThrows(IllegalStateException.class,()->service.save(jwt,update(version,"REPLACE"),new MockMultipartFile("logo",out.toByteArray())));
  tx.executeWithoutResult(s->{var c=factory.getCurrentSession().get(CustomerEntity.class,id);assertNull(c.getBusinessDisplayName());assertEquals("original",c.getLogoRevision());assertEquals(version,c.getVersion());assertArrayEquals(new byte[]{1,2,3},factory.getCurrentSession().get(BusinessLogo.class,id).getImage());});
 }
 @Test void failedAuditRollsBackLogoRemoval(){doThrow(new IllegalStateException("test")).when(audit).record(any(),any(),any(),any(),any(),any(),any(),any());assertThrows(IllegalStateException.class,()->service.save(jwt,update(version,"REMOVE"),null));tx.executeWithoutResult(s->assertNotNull(factory.getCurrentSession().get(BusinessLogo.class,id)));}
 @Test void simultaneousSavesCannotOverwriteEachOther()throws Exception{
  var pool=java.util.concurrent.Executors.newFixedThreadPool(2);var ready=new java.util.concurrent.CountDownLatch(2);var start=new java.util.concurrent.CountDownLatch(1);
  java.util.concurrent.Callable<Boolean> task=()->{ready.countDown();if(!start.await(5,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("timeout");try{service.save(jwt,update(version,"KEEP"),null);return true;}catch(ArenaOpsException ex){assertEquals(ErrorCode.CONFLICT,ex.getErrorCode());return false;}};
  try{var first=pool.submit(task);var second=pool.submit(task);assertTrue(ready.await(5,java.util.concurrent.TimeUnit.SECONDS));start.countDown();int successes=(first.get(10,java.util.concurrent.TimeUnit.SECONDS)?1:0)+(second.get(10,java.util.concurrent.TimeUnit.SECONDS)?1:0);assertEquals(1,successes);verify(audit,times(1)).record(any(),any(),any(),any(),any(),any(),any(),any());}finally{start.countDown();pool.shutdownNow();}
 }
 @Test void committedSaveAdvancesVersionAndRejectsStaleDraft(){var saved=service.save(jwt,update(version,"REMOVE"),null);assertTrue(saved.version()>version);assertEquals(ErrorCode.CONFLICT,assertThrows(ArenaOpsException.class,()->service.save(jwt,update(version,"KEEP"),null)).getErrorCode());tx.executeWithoutResult(s->{assertEquals("Public name",factory.getCurrentSession().get(CustomerEntity.class,id).getBusinessDisplayName());assertNull(factory.getCurrentSession().get(BusinessLogo.class,id));});}
}
