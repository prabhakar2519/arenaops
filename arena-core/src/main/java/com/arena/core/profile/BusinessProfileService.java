package com.arena.core.profile;
import com.arena.core.entity.*;
import com.arena.core.repository.*;
import com.arena.core.service.*;
import com.arena.core.training.*;
import com.arena.core.exception.*;
import com.arena.core.enums.AuditActorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;
@Service @RequiredArgsConstructor @Transactional
public class BusinessProfileService {
 private final AcademyAuthorization authorization;
 private final CustomerRepository customers;
 private final BusinessLogoRepository logos;
 private final LogoImages images;
 private final AuditService audit;
 private final AppUserRepository users;
 private final AdminAuthorizationService identities;
 private final CustomerAccessService access;
 private final AcademyStaffRepository memberships;
 public record Permissions(boolean viewProfile,boolean editProfile,boolean manageOrdinaryStaff,boolean delegateManagerAccess){}
 public record Branding(String displayName,String logoUrl,Permissions permissions){}
 @io.swagger.v3.oas.annotations.media.Schema(name="BusinessProfileView")
 public record Profile(Long version,String displayName,String businessType,String publicContactEmail,String publicPhone,String addressLine,String city,String region,String postalCode,String country,String logoUrl,Permissions permissions){}
 private CustomerEntity customer(Long id){return customers.findBillingCustomerForUpdate(id).orElseThrow(()->new ArenaOpsException(ErrorCode.ACCESS_DENIED));}
 private Permissions permissions(AcademyAuthorization.Actor actor){return new Permissions(actor.operations(),actor.operations(),actor.operations(),actor.owner());}
 private String name(CustomerEntity c){return Optional.ofNullable(c.getBusinessDisplayName()).filter(v->!v.isBlank()).orElse(Optional.ofNullable(c.getOrganizationName()).filter(v->!v.isBlank()).orElse(c.getCustomerName()));}
 private String logo(CustomerEntity c){return c.getLogoRevision()==null?null:"/api/business-profile/logo?revision="+c.getLogoRevision();}
 private Profile response(CustomerEntity c,AcademyAuthorization.Actor a){return new Profile(c.getVersion(),name(c),c.getBusinessType(),c.getPublicContactEmail(),c.getPublicPhone(),c.getAddressLine(),c.getCity(),c.getRegion(),c.getPostalCode(),c.getCountry(),logo(c),permissions(a));}
 public Profile get(Jwt jwt){var actor=authorization.operations(jwt);return response(customer(actor.academyId()),actor);}
 public Branding branding(Jwt jwt){
  if(jwt==null||jwt.getSubject()==null||jwt.getSubject().isBlank())throw new ArenaOpsException(ErrorCode.ACCESS_DENIED);
  // Existing legacy club staff lack academy membership; expose only their current tenant's name/logo.
  var u=users.findByUsernameIgnoreCase(identities.resolveUsername(jwt)).orElseThrow(()->new ArenaOpsException(ErrorCode.ACCESS_DENIED));
  if(u.getCustomerId()==null||!Boolean.TRUE.equals(u.getIsActive())||(u.getKeycloakSubject()!=null&&!u.getKeycloakSubject().equals(jwt.getSubject())))throw new ArenaOpsException(ErrorCode.ACCESS_DENIED);
  var c=customer(u.getCustomerId());access.assertCustomerCanUseApp(u);
  if(memberships.findByAppUserId(u.getId()).isPresent()||"OWNER".equals(u.getRole())||"ACADEMY".equalsIgnoreCase(c.getOrganizationType())||"ACADEMY".equalsIgnoreCase(c.getSports())){
   var actor=authorization.actor(jwt);return new Branding(name(c),logo(c),permissions(actor));
  }
  return new Branding(name(c),logo(c),new Permissions(false,false,false,false));
 }
 public byte[] image(Jwt jwt){branding(jwt);var u=users.findByUsernameIgnoreCase(identities.resolveUsername(jwt)).orElseThrow(()->new ArenaOpsException(ErrorCode.ACCESS_DENIED));return logos.findById(u.getCustomerId()).orElseThrow(()->new ArenaOpsException(ErrorCode.RESOURCE_NOT_FOUND)).getImage();}
 public Profile save(Jwt jwt,ProfileUpdate r,MultipartFile file){
  var actor=authorization.operations(jwt);var c=customer(actor.academyId());
  if(!Objects.equals(c.getVersion(),r.version()))throw new ArenaOpsException(ErrorCode.CONFLICT);
  if(("REPLACE".equals(r.logoAction()))!=(file!=null&&!file.isEmpty()))throw new ArenaOpsException(ErrorCode.INVALID_REQUEST);
  byte[] normalized="REPLACE".equals(r.logoAction())?images.normalize(file):null;
  var changes=new ArrayList<String>();
  change(changes,"displayName",name(c),r.displayName());change(changes,"businessType",c.getBusinessType(),empty(r.businessType()));
  change(changes,"publicContactEmail",c.getPublicContactEmail(),empty(r.publicContactEmail()));change(changes,"publicPhone",c.getPublicPhone(),empty(r.publicPhone()));
  change(changes,"addressLine",c.getAddressLine(),empty(r.addressLine()));change(changes,"city",c.getCity(),empty(r.city()));change(changes,"region",c.getRegion(),empty(r.region()));change(changes,"postalCode",c.getPostalCode(),empty(r.postalCode()));change(changes,"country",c.getCountry(),empty(r.country()));
  c.setBusinessDisplayName(r.displayName());c.setBusinessType(empty(r.businessType()));c.setPublicContactEmail(empty(r.publicContactEmail()));c.setPublicPhone(empty(r.publicPhone()));c.setAddressLine(empty(r.addressLine()));c.setCity(empty(r.city()));c.setRegion(empty(r.region()));c.setPostalCode(empty(r.postalCode()));c.setCountry(empty(r.country()));
  if(normalized!=null){var stored=logos.findById(c.getId()).orElseGet(BusinessLogo::new);stored.setCustomerId(c.getId());stored.setImage(normalized);logos.save(stored);c.setLogoRevision(UUID.randomUUID().toString());changes.add("logoReplaced");}
  else if("REMOVE".equals(r.logoAction())&&c.getLogoRevision()!=null){logos.deleteById(c.getId());c.setLogoRevision(null);changes.add("logoRemoved");}
  customers.saveAndFlush(c);
  if(!changes.isEmpty())audit.record("BUSINESS_PROFILE_UPDATED",c.getId(),AuditActorType.CUSTOMER,String.valueOf(actor.user().getId()),null,null,null,"changedFields="+String.join(",",changes));
  return response(c,actor);
 }
 private String empty(String v){return v==null||v.isBlank()?null:v;}
 private void change(List<String> changes,String field,Object before,Object after){if(!Objects.equals(before,after))changes.add(field);}
}
