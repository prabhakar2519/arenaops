package com.arena.core.training;

import com.arena.core.entity.AppUserEntity;
import com.arena.core.enums.AuditActorType;
import com.arena.core.exception.*;
import com.arena.core.repository.*;
import com.arena.core.service.*;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.oauth2.jwt.Jwt;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class StaffLoginService {
 private final AcademyAuthorization authorization;
 private final CustomerRepository customers;
 private final AcademyStaffRepository staff;
 private final AppUserRepository users;
 private final KeycloakService identities;
 private final AccountSetupNotification notifications;
 private final AuditService audit;
 private final TrainingBatchRepository batches;
 private static final int LIFESPAN=86400;
 public record Login(@NotNull Long version,@NotBlank @Pattern(regexp="[A-Za-z0-9._@+-]+") @Size(max=100) String username,@NotBlank @Email @Size(max=255) String email,@NotEmpty Set<@Pattern(regexp="MANAGER|INSTRUCTOR|STAFF") String> roles){}
 public record Version(@NotNull Long version){}
 private AcademyStaff member(Jwt jwt,Long id,Long version){var a=authorization.operations(jwt);customers.findBillingCustomerForUpdate(a.academyId()).orElseThrow(()->new ArenaOpsException(ErrorCode.ACCESS_DENIED));var m=staff.findById(id).filter(v->Objects.equals(v.getAcademyId(),a.academyId())).orElseThrow(()->new ArenaOpsException(ErrorCode.RESOURCE_NOT_FOUND));authorization.personnel(a,m,null,null);if(!Objects.equals(m.getVersion(),version))throw new ArenaOpsException(ErrorCode.CONFLICT);return m;}
 @Transactional(noRollbackFor=ArenaOpsException.class)
 public AcademyStaff enable(Jwt jwt,Long id,Login r){
  var m=member(jwt,id,r.version());authorization.personnel(authorization.operations(jwt),m,null,r.roles());if(!m.isActive())throw new ArenaOpsException(ErrorCode.INVALID_STATE);
  if(!Set.of("NOT_ENABLED","PROVISIONING_FAILED").contains(m.getLoginState())||m.getAppUserId()!=null)throw new ArenaOpsException(ErrorCode.INVALID_STATE);
  if(r.roles()==null||r.roles().isEmpty()||!Set.of("MANAGER","INSTRUCTOR","STAFF").containsAll(r.roles()))throw new ArenaOpsException(ErrorCode.ACCESS_DENIED);
  String username=r.username().trim().toLowerCase(Locale.ROOT);
  assertAvailable(username,r.email().trim().toLowerCase(Locale.ROOT),m.getAcademyId(),id);
  if(m.getLoginUsername()!=null&&!m.getLoginUsername().equals(username))throw new ArenaOpsException(ErrorCode.INVALID_STATE);
  m.setLoginUsername(username);m.setLoginEmail(r.email().trim().toLowerCase(Locale.ROOT));m.setEmail(r.email().trim());m.setApplicationRoles(new HashSet<>(r.roles()));m.setLoginState("PROVISIONING");staff.saveAndFlush(m);
  try {
   var academy=customers.findById(m.getAcademyId()).orElseThrow();
   String subject=identities.provisionAcademyStaff(username,m.getEmail(),m.getName(),m.getAcademyId(),m.getId(),academy.getCustomerName());
   m.setKeycloakSubject(subject);
   var user=users.saveAndFlush(AppUserEntity.builder().username(username).email(m.getEmail()).displayName(m.getName()).customerId(m.getAcademyId()).role("STAFF").keycloakSubject(subject).passwordHash("!KEYCLOAK_MANAGED").isActive(false).build());
   m.setAppUserId(user.getId());m.setLoginState("PENDING");event(jwt,m,"ACCOUNT_PROVISIONED");
  } catch(ArenaOpsException ex){m.setLoginState("PROVISIONING_FAILED");staff.saveAndFlush(m);event(jwt,m,"PROVISIONING_FAILED");throw ex;}
  staff.saveAndFlush(m);return send(jwt,m);
 }
 /** Call after tenant authorization; exact ownership permits only recovery of this staff intent. */
 public void assertAvailable(String username,String email,Long academy,Long staffId){
  if(users.findByUsernameIgnoreCase(username).isPresent()||staff.findByLoginUsernameIgnoreCase(username).filter(v->!Objects.equals(v.getId(),staffId)).isPresent())throw new ArenaOpsException(ErrorCode.DUPLICATE_USERNAME);
  if(users.existsByNormalizedEmail(email)||staff.findByLoginEmail(email).filter(v->!Objects.equals(v.getId(),staffId)).isPresent())throw new ArenaOpsException(ErrorCode.DUPLICATE_EMAIL);
  identities.validateAcademyStaffIdentifiers(username,email,academy,staffId);
 }
 private AcademyStaff send(Jwt jwt,AcademyStaff m){
  m.setNotificationState("SENDING");staff.saveAndFlush(m);
  try {notifications.send(m.getKeycloakSubject(),LIFESPAN);m.setNotificationState("SENT");m.setInvitationSentAt(LocalDateTime.now());m.setInvitationExpiresAt(LocalDateTime.now().plusSeconds(LIFESPAN));event(jwt,m,"SETUP_EMAIL_SENT");}
  catch(ArenaOpsException ex){m.setNotificationState("FAILED");event(jwt,m,"SETUP_EMAIL_FAILED");staff.saveAndFlush(m);throw ex;}
  return staff.saveAndFlush(m);
 }
 @Transactional(noRollbackFor=ArenaOpsException.class)
 public AcademyStaff resend(Jwt jwt,Long id,Version r){var m=member(jwt,id,r.version());if(!"PENDING".equals(m.getLoginState()))throw new ArenaOpsException(ErrorCode.INVALID_STATE);if("SENT".equals(m.getNotificationState())&&m.getInvitationExpiresAt()!=null&&m.getInvitationExpiresAt().isAfter(LocalDateTime.now()))throw new ArenaOpsException(ErrorCode.CONFLICT);return send(jwt,m);}
 @Transactional(noRollbackFor=ArenaOpsException.class)
 public AcademyStaff disable(Jwt jwt,Long id,Version r){var m=member(jwt,id,r.version());if(m.getKeycloakSubject()==null)throw new ArenaOpsException(ErrorCode.INVALID_STATE);m.setLoginState("DISABLED");m.setAccessValidAfter(LocalDateTime.now().withNano(0).plusSeconds(1));users.findById(m.getAppUserId()).ifPresent(u->{u.setIsActive(false);users.saveAndFlush(u);});staff.saveAndFlush(m);event(jwt,m,"LOGIN_DISABLED");identities.setAcademyIdentityEnabled(m.getKeycloakSubject(),false);return m;}
 @Transactional
 public AcademyStaff resume(Jwt jwt,Long id,Version r){var m=member(jwt,id,r.version());if(!"DISABLED".equals(m.getLoginState())||!m.isActive())throw new ArenaOpsException(ErrorCode.INVALID_STATE);identities.setAcademyIdentityEnabled(m.getKeycloakSubject(),true);m.setAccessValidAfter(LocalDateTime.now().withNano(0).plusSeconds(1));m.setLoginState("PENDING");event(jwt,m,"LOGIN_ENABLED");return staff.saveAndFlush(m);}
 @Transactional
 public AcademyStaff roles(Jwt jwt,Long id,Set<String> roles,Long version){authorization.owner(jwt);var m=member(jwt,id,version);if(m.getRegistrationRequestId()!=null&&m.getAppUserId()==null)throw new ArenaOpsException(ErrorCode.INVALID_STATE);if(roles==null||roles.isEmpty()||!Set.of("MANAGER","INSTRUCTOR","STAFF").containsAll(roles))throw new ArenaOpsException(ErrorCode.ACCESS_DENIED);if(!"COACH".equals(m.getRole())&&!roles.contains("INSTRUCTOR")){if(batches.findByAcademyIdOrderByName(m.getAcademyId()).stream().anyMatch(b->!"INACTIVE".equals(b.getStatus())&&b.getCoachIds().contains(id)))throw new ArenaOpsException(ErrorCode.TRAINING_DEPENDENCY_EXISTS);m.getSportIds().clear();}if(!m.getApplicationRoles().contains("MANAGER")||!roles.contains("MANAGER"))m.setManagerAccess("STANDARD");m.setApplicationRoles(new HashSet<>(roles));event(jwt,m,"ROLES_CHANGED");return staff.saveAndFlush(m);}
 @Transactional
 public AcademyStaff managerAccess(Jwt jwt,Long id,String level,Long version){
  var actor=authorization.owner(jwt);
  if(!Set.of("STANDARD","FULL_OPERATIONS").contains(level))throw new ArenaOpsException(ErrorCode.INVALID_REQUEST);
  var m=member(jwt,id,version);
  if(!m.getApplicationRoles().contains("MANAGER"))throw new ArenaOpsException(ErrorCode.INVALID_STATE);
  String previous=m.getManagerAccess();m.setManagerAccess(level);staff.saveAndFlush(m);
  audit.record("ACADEMY_MANAGER_ACCESS_CHANGED",actor.academyId(),AuditActorType.CUSTOMER,String.valueOf(actor.user().getId()),previous,level,null,"staffId="+m.getId());
  return m;
 }
 /** Called only with a signature/issuer-verified JWT; a completed required-action login proves credentials exist. */
 @Transactional
 public void reconcile(Jwt jwt,AppUserEntity user){
  if("OWNER".equals(user.getRole())||"ADMIN".equals(user.getRole()))return;
  var membership=staff.findByAppUserId(user.getId());
  if(membership.isEmpty()){
   if(user.getKeycloakSubject()!=null&&!user.getKeycloakSubject().equals(jwt.getSubject()))throw new ArenaOpsException(ErrorCode.ACCESS_DENIED);
   if(user.getCustomerId()==null)return;
   var customer=customers.findById(user.getCustomerId()).orElseThrow(()->new ArenaOpsException(ErrorCode.ACCESS_DENIED));
   if(!"ACADEMY".equalsIgnoreCase(customer.getOrganizationType())&&!"ACADEMY".equalsIgnoreCase(customer.getSports()))return;
   throw new ArenaOpsException(ErrorCode.ACCESS_DENIED);
  }
  var m=membership.get();
  if(!m.isActive())throw new ArenaOpsException(ErrorCode.ACCESS_DENIED);
  customers.findBillingCustomerForUpdate(m.getAcademyId()).orElseThrow(()->new ArenaOpsException(ErrorCode.ACCESS_DENIED));
  if("LEGACY_LINKED".equals(m.getLoginState())){
   if(!Objects.equals(m.getAcademyId(),user.getCustomerId())||!Boolean.TRUE.equals(jwt.getClaim("email_verified"))||!Objects.equals(identities.getKeycloakUserId(user.getUsername()),jwt.getSubject())||!identities.academyStaffSetupComplete(jwt.getSubject()))throw new ArenaOpsException(ErrorCode.ACCESS_DENIED);
   m.setKeycloakSubject(jwt.getSubject());user.setKeycloakSubject(jwt.getSubject());
   if(m.getApplicationRoles().isEmpty())m.setApplicationRoles(new HashSet<>(Set.of("COACH".equals(m.getRole())?"INSTRUCTOR":"STAFF")));
   m.setLoginState("ACTIVE");staff.saveAndFlush(m);users.saveAndFlush(user);event(jwt,m,"LEGACY_LOGIN_VERIFIED");
  }
  if(!Objects.equals(m.getKeycloakSubject(),jwt.getSubject())||!Objects.equals(user.getCustomerId(),m.getAcademyId())||"DISABLED".equals(m.getLoginState()))throw new ArenaOpsException(ErrorCode.ACCESS_DENIED);
  if(m.getAccessValidAfter()!=null&&(jwt.getIssuedAt()==null||jwt.getIssuedAt().isBefore(m.getAccessValidAfter().atZone(java.time.ZoneId.systemDefault()).toInstant())))throw new ArenaOpsException(ErrorCode.ACCESS_DENIED);
  if("PENDING".equals(m.getLoginState())){
   if(!Boolean.TRUE.equals(jwt.getClaim("email_verified"))||!identities.academyStaffSetupComplete(m.getKeycloakSubject()))throw new ArenaOpsException(ErrorCode.INVALID_STATE);
   m.setLoginState("ACTIVE");user.setIsActive(true);users.saveAndFlush(user);staff.saveAndFlush(m);event(jwt,m,"LOGIN_ACTIVATED");
  }
  if(!"ACTIVE".equals(m.getLoginState()))throw new ArenaOpsException(ErrorCode.ACCESS_DENIED);
 }
 private void event(Jwt jwt,AcademyStaff m,String action){audit.record("ACADEMY_STAFF_"+action,m.getAcademyId(),AuditActorType.CUSTOMER,jwt.getSubject(),null,null,null,"staffId="+m.getId());}
}
