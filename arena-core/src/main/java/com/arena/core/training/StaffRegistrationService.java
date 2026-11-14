package com.arena.core.training;

import com.arena.core.entity.AppUserEntity;
import com.arena.core.enums.AuditActorType;
import com.arena.core.exception.*;
import com.arena.core.repository.*;
import com.arena.core.service.*;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Local durable intent -> owned Keycloak identity -> atomic local activation.
 * The inactive intent survives crashes/DB rollback and is the identity's recovery anchor.
 * No remote account is ever deleted as compensation. */
@Service
@org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
public class StaffRegistrationService {
 private final AcademyAuthorization auth;private final CustomerRepository customers;private final AcademyStaffRepository staff;private final AppUserRepository users;private final StaffLoginService login;private final KeycloakService identities;private final AuditService audit;private final TransactionTemplate tx;
 public StaffRegistrationService(AcademyAuthorization auth,CustomerRepository customers,AcademyStaffRepository staff,AppUserRepository users,StaffLoginService login,KeycloakService identities,AuditService audit,PlatformTransactionManager manager){this.auth=auth;this.customers=customers;this.staff=staff;this.users=users;this.login=login;this.identities=identities;this.audit=audit;this.tx=new TransactionTemplate(manager);tx.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);}
 public record Registration(@NotNull UUID requestId,@NotBlank @Size(max=160) String name,@NotBlank @Email @Size(max=255) String email,@Size(max=50) String phone,@NotBlank @Pattern(regexp="COACH|STAFF") String role,@Pattern(regexp="MANAGER|COACH|RECEPTIONIST|GENERAL_STAFF") String designation,@NotBlank @Pattern(regexp="[A-Za-z0-9._@+-]+") @Size(max=100) String username,@NotEmpty Set<@NotNull @Pattern(regexp="MANAGER|INSTRUCTOR|STAFF") String> roles){}
 public record Availability(@NotBlank @Pattern(regexp="[A-Za-z0-9._@+-]+") @Size(max=100) String username,@NotBlank @Email @Size(max=255) String email,UUID requestId,Long staffId){}
 public void available(Jwt jwt,Availability r){tx.executeWithoutResult(status->{var a=auth.operations(jwt);AcademyStaff m=null;if(r.staffId()!=null)m=owned(a,r.staffId());else if(r.requestId()!=null)m=staff.findByAcademyIdAndRegistrationRequestId(a.academyId(),r.requestId().toString()).orElse(null);if(m!=null){auth.personnel(a,m,null,null);if(m.getAppUserId()!=null&&Objects.equals(m.getLoginUsername(),normalized(r.username()))&&Objects.equals(m.getLoginEmail(),normalized(r.email())))return;if(m.getAppUserId()!=null)throw new ArenaOpsException(ErrorCode.INVALID_STATE);}login.assertAvailable(normalized(r.username()),normalized(r.email()),a.academyId(),m==null?null:m.getId());});}
 public AcademyStaff register(Jwt jwt,Registration raw){var r=canonical(raw);Long id;
  try{id=tx.execute(status->{var a=auth.operations(jwt);customers.findBillingCustomerForUpdate(a.academyId()).orElseThrow(()->new ArenaOpsException(ErrorCode.ACCESS_DENIED));var prior=staff.findByAcademyIdAndRegistrationRequestId(a.academyId(),r.requestId().toString());if(prior.isPresent()){var m=prior.get();auth.personnel(a,m,r.designation(),r.roles());if(!fingerprint(r).equals(m.getRegistrationHash()))throw new ArenaOpsException(ErrorCode.CONFLICT);return m.getId();}
   var m=new AcademyStaff();m.setAcademyId(a.academyId());m.setName(r.name());m.setEmail(r.email());m.setPhone(r.phone());m.setRole(r.role());m.setDesignation(r.designation());m.setActive(false);m.setLoginState("PROVISIONING");m.setLoginUsername(r.username());m.setLoginEmail(r.email());m.setApplicationRoles(new HashSet<>(r.roles()));m.setRegistrationRequestId(r.requestId().toString());m.setRegistrationHash(fingerprint(r));m.setCreatedBy(a.user().getUsername());m.setUpdatedBy(a.user().getUsername());auth.personnel(a,m,r.designation(),r.roles());login.assertAvailable(r.username(),r.email(),a.academyId(),null);staff.saveAndFlush(m);event(jwt,m,"REGISTRATION_STARTED");return m.getId();});}
  catch(org.springframework.dao.DataIntegrityViolationException e){available(jwt,new Availability(r.username(),r.email(),r.requestId(),null));throw new ArenaOpsException(ErrorCode.STAFF_REGISTRATION_UNCERTAIN);}
  AcademyStaff complete;
  try{complete=tx.execute(status->{var a=auth.operations(jwt);customers.findBillingCustomerForUpdate(a.academyId()).orElseThrow(()->new ArenaOpsException(ErrorCode.ACCESS_DENIED));var m=owned(a,id);auth.personnel(a,m,r.designation(),r.roles());if(m.getAppUserId()!=null)return m;
    login.assertAvailable(r.username(),r.email(),a.academyId(),id);var club=customers.findById(a.academyId()).orElseThrow();String subject=identities.provisionAcademyStaff(r.username(),r.email(),r.name(),a.academyId(),id,club.getCustomerName());
    var user=users.saveAndFlush(AppUserEntity.builder().username(r.username()).email(r.email()).displayName(r.name()).customerId(a.academyId()).role("STAFF").keycloakSubject(subject).passwordHash("!KEYCLOAK_MANAGED").isActive(false).build());m.setAppUserId(user.getId());m.setKeycloakSubject(subject);m.setLoginState("PENDING");m.setActive(true);staff.saveAndFlush(m);event(jwt,m,"REGISTRATION_COMPLETED");return m;});}
  catch(RuntimeException failure){
   // A commit response can be lost. Re-read before reporting failure or changing state.
   try{var persisted=tx.execute(status->{var a=auth.operations(jwt);var m=owned(a,id);if(m.getAppUserId()==null){m.setLoginState("PROVISIONING_FAILED");staff.saveAndFlush(m);}return m;});if(persisted.getAppUserId()!=null)return notifySetup(jwt,persisted);}
   catch(RuntimeException uncertain){throw new ArenaOpsException(ErrorCode.STAFF_REGISTRATION_UNCERTAIN);}
   if(failure instanceof ArenaOpsException known)throw known;
   throw new ArenaOpsException(ErrorCode.STAFF_REGISTRATION_UNCERTAIN);
  }
  return notifySetup(jwt,complete);
 }
 public AcademyStaff retry(Jwt jwt,Long id){var r=tx.execute(status->{var a=auth.operations(jwt);var m=owned(a,id);auth.personnel(a,m,null,null);if(m.getRegistrationRequestId()==null)throw new ArenaOpsException(ErrorCode.INVALID_STATE);return new Registration(UUID.fromString(m.getRegistrationRequestId()),m.getName(),m.getLoginEmail(),m.getPhone(),m.getRole(),m.getDesignation(),m.getLoginUsername(),Set.copyOf(m.getApplicationRoles()));});return register(jwt,r);}
 private AcademyStaff notifySetup(Jwt jwt,AcademyStaff m){if("PENDING".equals(m.getLoginState())&&Set.of("NOT_SENT","FAILED").contains(m.getNotificationState())){
   try{return login.resend(jwt,m.getId(),new StaffLoginService.Version(m.getVersion()));}
   catch(RuntimeException delivery){ // Registration is already committed. Never report it as a failed create.
    try{return tx.execute(status->owned(auth.operations(jwt),m.getId()));}catch(RuntimeException unavailable){return m;}
   }}return m;}
 private AcademyStaff owned(AcademyAuthorization.Actor a,Long id){return staff.findById(id).filter(m->Objects.equals(m.getAcademyId(),a.academyId())).orElseThrow(()->new ArenaOpsException(ErrorCode.RESOURCE_NOT_FOUND));}
 private static String normalized(String s){return s.trim().toLowerCase(Locale.ROOT);}
 private static Registration canonical(Registration r){String designation=r.designation()==null?("COACH".equals(r.role())?"COACH":"GENERAL_STAFF"):r.designation();return new Registration(r.requestId(),r.name().trim(),normalized(r.email()),r.phone()==null||r.phone().isBlank()?null:r.phone().trim(),"COACH".equals(designation)?"COACH":"STAFF",designation,normalized(r.username()),Set.copyOf(r.roles()));}
 private static String fingerprint(Registration r){try{var digest=MessageDigest.getInstance("SHA-256");for(String value:List.of(r.name(),r.email(),r.phone()==null?"":r.phone(),r.role(),r.designation(),r.username(),String.join(",",new TreeSet<>(r.roles())))){digest.update((value.length()+":").getBytes(StandardCharsets.UTF_8));digest.update(value.getBytes(StandardCharsets.UTF_8));}return HexFormat.of().formatHex(digest.digest());}catch(java.security.NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}}
 private void event(Jwt jwt,AcademyStaff m,String name){audit.record("ACADEMY_STAFF_"+name,m.getAcademyId(),AuditActorType.CUSTOMER,jwt.getSubject(),null,null,null,"staffId="+m.getId());}
}
