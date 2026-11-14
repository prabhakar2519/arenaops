package com.arena.core.training;

import com.arena.core.entity.CustomerEntity;
import com.arena.core.exception.*;
import com.arena.core.repository.CustomerRepository;
import com.arena.core.service.AuditService;
import com.arena.core.enums.AuditActorType;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.oauth2.jwt.Jwt;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional
public class AcademySetupService {
 private final AcademyAuthorization authorization;
 private final CustomerRepository customers;
 private final AcademySportRepository sports;
 private final AcademyStaffRepository staff;
 private final TrainingBatchRepository batches;
 private final BatchScheduleRepository schedules;
 private final AcademyPlayAreaRepository facilities;
 private final AuditService audit;
 @io.swagger.v3.oas.annotations.media.Schema(name="AcademySetupProfileRequest")
 public record Profile(@NotNull Long version,@NotBlank @Size(max=255) String name,@NotBlank @Size(max=255) String ownerName,@NotBlank @Email String email,@NotBlank @Size(max=50) String phone,@NotBlank String timezone){}
 public record Confirmation(@NotNull Long version,boolean ownerManaged,boolean reviewConfirmed){}
 public record Step(String key,String label,boolean applicable,boolean complete,String path){}
 public CustomerEntity academy(Long id){CustomerEntity c=customers.findBillingCustomerForUpdate(id).orElseThrow(()->new ArenaOpsException(ErrorCode.ACCESS_DENIED));if(!"ACADEMY".equalsIgnoreCase(c.getOrganizationType())&&!"ACADEMY".equalsIgnoreCase(c.getSports()))throw new ArenaOpsException(ErrorCode.ACCESS_DENIED);return c;}
 private boolean present(String s){return s!=null&&!s.isBlank();}
 public Map<String,Object> progress(Jwt jwt){return evaluate(academy(authorization.manage(jwt).academyId()));}
 public Map<String,Object> evaluate(CustomerEntity c){
  var active=sports.findByAcademyIdOrderByName(c.getId()).stream().filter(AcademySport::isActive).toList();
  var training=active.stream().filter(AcademySport::isTraining).toList();var booking=active.stream().filter(AcademySport::isSessions).toList();
  var bs=batches.findByAcademyIdOrderByName(c.getId());var fs=facilities.findByAcademyIdOrderByName(c.getId());
  boolean validBatches=training.isEmpty()||training.stream().anyMatch(s->bs.stream().anyMatch(b->Objects.equals(b.getSportId(),s.getId())&&"ACTIVE".equals(b.getStatus())&&!schedules.findByBatchIdAndActiveTrueOrderByDayOfWeekAscStartTimeAsc(b.getId()).isEmpty()&&(!b.getCoachIds().isEmpty()||Boolean.TRUE.equals(c.getOwnerManaged()))));
  boolean validAreas=booking.isEmpty()||booking.stream().anyMatch(s->fs.stream().anyMatch(f->Objects.equals(f.getSportId(),s.getId())&&f.isActive()&&(f.getFacilityType()==null||com.arena.core.facility.FacilityContracts.configured(f))));
  var steps=new ArrayList<Step>();
  steps.add(new Step("PROFILE","Academy profile",true,present(c.getCustomerName())&&present(c.getOwnerName())&&present(c.getOwnerEmail())&&present(c.getOwnerPhone()),"/academy/setup"));
  steps.add(new Step("SPORTS","Sports & activities",true,!active.isEmpty(),"/academy/sports"));
  steps.add(new Step("STAFF","Staff or owner-managed",true,Boolean.TRUE.equals(c.getOwnerManaged())||staff.findByAcademyIdOrderByName(c.getId()).stream().anyMatch(AcademyStaff::isActive),"/academy/staff"));
  steps.add(new Step("BATCHES","Training batches",!training.isEmpty(),validBatches,"/academy/batches"));
  steps.add(new Step("FACILITIES","Play areas",!booking.isEmpty(),validAreas,"/academy/facilities"));
  boolean prerequisites=steps.stream().filter(Step::applicable).allMatch(Step::complete);
  // A lost prerequisite invalidates review. Repairing it requires a fresh owner review.
  if(!prerequisites)c.setSetupReviewConfirmed(false);
  steps.add(new Step("REVIEW","Setup review",true,prerequisites&&Boolean.TRUE.equals(c.getSetupReviewConfirmed()),"/academy/setup"));
  long applicable=steps.stream().filter(Step::applicable).count(),completed=steps.stream().filter(Step::applicable).filter(Step::complete).count();
  String status=completed==applicable?"READY":prerequisites?"REVIEW_NEEDED":"INCOMPLETE";
  c.setSetupStatus(status);c.setSetupEvaluatedAt(LocalDateTime.now());customers.saveAndFlush(c);
  return Map.of("percentage",Math.round(100.0*completed/applicable),"status",status,"steps",steps,"ownerManaged",c.getOwnerManaged(),"reviewConfirmed",c.getSetupReviewConfirmed(),"evaluatedAt",c.getSetupEvaluatedAt(),"version",c.getVersion(),"profile",Map.of("name",c.getCustomerName(),"ownerName",Objects.toString(c.getOwnerName(),""),"email",Objects.toString(c.getOwnerEmail(),""),"phone",Objects.toString(c.getOwnerPhone(),""),"timezone",c.getTrainingTimezone()));
 }
 public Map<String,Object> profile(Jwt jwt,Profile r){var a=authorization.owner(jwt);var c=academy(a.academyId());if(!Objects.equals(c.getVersion(),r.version()))throw new ArenaOpsException(ErrorCode.CONFLICT);if(!ZoneId.getAvailableZoneIds().contains(r.timezone()))throw new ArenaOpsException(ErrorCode.INVALID_REQUEST);c.setCustomerName(r.name().trim());c.setOrganizationName(r.name().trim());c.setOwnerName(r.ownerName().trim());c.setOwnerEmail(r.email().trim());c.setOwnerPhone(r.phone().trim());c.setTrainingTimezone(r.timezone());c.setSetupReviewConfirmed(false);event(jwt,c,"PROFILE");return evaluate(c);}
 public Map<String,Object> confirm(Jwt jwt,Confirmation r){var a=authorization.owner(jwt);var c=academy(a.academyId());if(!Objects.equals(c.getVersion(),r.version()))throw new ArenaOpsException(ErrorCode.CONFLICT);c.setOwnerManaged(r.ownerManaged());c.setSetupReviewConfirmed(false);var current=evaluate(c);if(r.reviewConfirmed()&&!"REVIEW_NEEDED".equals(current.get("status")))throw new ArenaOpsException(ErrorCode.INVALID_STATE);c.setSetupReviewConfirmed(r.reviewConfirmed());event(jwt,c,"REVIEW");return evaluate(c);}
 private void event(Jwt jwt,CustomerEntity c,String action){audit.record("ACADEMY_SETUP_"+action,c.getId(),AuditActorType.CUSTOMER,jwt.getSubject(),null,null,null,null);}
}
