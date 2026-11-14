package com.arena.core.training;

import com.arena.core.entity.*;
import com.arena.core.exception.*;
import com.arena.core.repository.*;
import com.arena.core.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.security.oauth2.jwt.Jwt;
import java.util.*;

@Service @RequiredArgsConstructor @org.springframework.transaction.annotation.Transactional(noRollbackFor=ArenaOpsException.class)
public class AcademyAuthorization {
 private final AppUserRepository users;
 private final CustomerRepository customers;
 private final AcademyStaffRepository staff;
 private final CustomerAccessService access;
 private final AdminAuthorizationService authorization;
 public record Actor(AppUserEntity user,AcademyStaff membership,Set<String> roles) {
  public Long academyId(){return user.getCustomerId();}
  public boolean owner(){return roles.contains("OWNER");}
  public boolean manager(){return owner()||roles.contains("MANAGER");}
  public boolean operations(){return owner()||(roles.contains("MANAGER")&&membership!=null&&"FULL_OPERATIONS".equals(membership.getManagerAccess()));}
 }
 public Actor actor(Jwt jwt) {
  if(jwt==null||jwt.getSubject()==null||jwt.getSubject().isBlank()) throw new ArenaOpsException(ErrorCode.ACCESS_DENIED);
  AppUserEntity u=users.findByUsernameIgnoreCase(authorization.resolveUsername(jwt)).orElseThrow(()->new ArenaOpsException(ErrorCode.ACCESS_DENIED));
  if(u.getCustomerId()==null||!Boolean.TRUE.equals(u.getIsActive())) throw new ArenaOpsException(ErrorCode.ACCESS_DENIED);
  customers.findBillingCustomerForUpdate(u.getCustomerId()).orElseThrow(()->new ArenaOpsException(ErrorCode.ACCESS_DENIED));
  access.assertCustomerCanUseApp(u);
  if("OWNER".equals(u.getRole())) {
   Object claim=jwt.getClaim("realm_access");
   if(!(claim instanceof Map<?,?> map && map.get("roles") instanceof Collection<?> roles && roles.contains("OWNER"))) throw new ArenaOpsException(ErrorCode.ACCESS_DENIED);
   if(u.getKeycloakSubject()!=null&&!u.getKeycloakSubject().equals(jwt.getSubject())) throw new ArenaOpsException(ErrorCode.ACCESS_DENIED);
   return new Actor(u,null,Set.of("OWNER"));
  }
  AcademyStaff m=staff.findByAppUserId(u.getId()).orElseThrow(()->new ArenaOpsException(ErrorCode.ACCESS_DENIED));
  if(!Objects.equals(m.getAcademyId(),u.getCustomerId())||!Objects.equals(m.getKeycloakSubject(),jwt.getSubject())||!m.isActive()||!"ACTIVE".equals(m.getLoginState())) throw new ArenaOpsException(ErrorCode.ACCESS_DENIED);
  if(u.getKeycloakSubject()!=null&&!u.getKeycloakSubject().equals(jwt.getSubject()))throw new ArenaOpsException(ErrorCode.ACCESS_DENIED);
  if(m.getAccessValidAfter()!=null&&(jwt.getIssuedAt()==null||jwt.getIssuedAt().isBefore(m.getAccessValidAfter().atZone(java.time.ZoneId.systemDefault()).toInstant())))throw new ArenaOpsException(ErrorCode.ACCESS_DENIED);
  return new Actor(u,m,Set.copyOf(m.getApplicationRoles()));
 }
 public Actor manage(Jwt jwt){Actor a=actor(jwt);if(!a.manager())throw new ArenaOpsException(ErrorCode.ACCESS_DENIED);return a;}
 public Actor operations(Jwt jwt){Actor a=actor(jwt);if(!a.operations())throw new ArenaOpsException(ErrorCode.ACCESS_DENIED);return a;}
 public void personnel(Actor actor,AcademyStaff target,String designation,Set<String> roles){
  if(!actor.operations()||!Objects.equals(actor.academyId(),target.getAcademyId()))throw new ArenaOpsException(ErrorCode.ACCESS_DENIED);
  if(actor.owner())return;
  boolean protectedUser=target.getAppUserId()!=null&&users.findById(target.getAppUserId()).map(u->"OWNER".equals(u.getRole())).orElse(false);
  if(protectedUser||"MANAGER".equals(target.getDesignation())||target.getApplicationRoles().contains("MANAGER")||"MANAGER".equals(designation)||(roles!=null&&roles.contains("MANAGER")))throw new ArenaOpsException(ErrorCode.ACCESS_DENIED);
 }
 public Actor owner(Jwt jwt){Actor a=actor(jwt);if(!a.owner())throw new ArenaOpsException(ErrorCode.ACCESS_DENIED);return a;}
}
