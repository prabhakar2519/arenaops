package com.arena.core.training;

import com.arena.core.entity.*;
import com.arena.core.enums.AuditActorType;
import com.arena.core.exception.*;
import com.arena.core.repository.*;
import com.arena.core.service.*;
import com.arena.core.sessions.AcademyRunningSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import static com.arena.core.training.TrainingRequests.*;

@Service @lombok.RequiredArgsConstructor(onConstructor_=@org.springframework.beans.factory.annotation.Autowired) @Transactional
public class TrainingService {
 private final AppUserRepository users;
 private final CustomerRepository customers;
 private final CustomerAccessService access;
 private final AdminAuthorizationService authorization;
 private final SportCatalogueRepository catalogue;
 private final AcademySportRepository sports;
 private final AcademyStaffRepository staff;
 private final TrainingBatchRepository batches;
 private final BatchScheduleRepository schedules;
 private final AuditService audit;
 private final AcademyAuthorization academyAuthorization;
 private final AcademySetupService setup;
 private final AcademyPlayAreaRepository facilities;
 private final AcademyRunningSessionRepository runningSessions;
 public TrainingService(AppUserRepository users,CustomerRepository customers,CustomerAccessService access,AdminAuthorizationService authorization,SportCatalogueRepository catalogue,AcademySportRepository sports,AcademyStaffRepository staff,TrainingBatchRepository batches,BatchScheduleRepository schedules,AuditService audit,AcademyAuthorization academyAuthorization,AcademySetupService setup,AcademyPlayAreaRepository facilities){this(users,customers,access,authorization,catalogue,sports,staff,batches,schedules,audit,academyAuthorization,setup,facilities,null);}

 private boolean role(Jwt jwt,String name) {
  Object claim=jwt.getClaim("realm_access");
  return claim instanceof Map<?,?> map && map.get("roles") instanceof Collection<?> roles && roles.contains(name);
 }
 private AppUserEntity owner(Jwt jwt) {return academyAuthorization.manage(jwt).user();}
 private CustomerEntity sportsScope(Jwt jwt){var actor=academyAuthorization.manage(jwt);return customers.findBillingCustomerForUpdate(actor.academyId()).orElseThrow(()->new ArenaOpsException(ErrorCode.ACCESS_DENIED));}
 private boolean academy(CustomerEntity c) {
  return "ACADEMY".equalsIgnoreCase(c.getOrganizationType()) || "ACADEMY".equalsIgnoreCase(c.getSports());
 }
 private CustomerEntity scope(Jwt jwt) {
  AppUserEntity user=owner(jwt);
  // Serialize all academy mutations, including membership, schedule and activation writes.
  CustomerEntity c=customers.findBillingCustomerForUpdate(user.getCustomerId()).orElseThrow(()->new ArenaOpsException(ErrorCode.ACCESS_DENIED));
  if(!academy(c)) throw new ArenaOpsException(ErrorCode.ACCESS_DENIED);
  access.assertCustomerCanUseApp(user);
  return c;
 }
 private boolean coaching(AcademyStaff m){return "COACH".equals(m.getRole())||m.getApplicationRoles().contains("INSTRUCTOR");}
 private void version(TrainingRecord e,Long supplied) {
  if(supplied==null || !Objects.equals(e.getVersion(),supplied)) throw new ArenaOpsException(ErrorCode.CONFLICT);
 }
 private <T extends TrainingRecord> T touched(T e,Jwt jwt) {
  String actor=authorization.resolveUsername(jwt);
  if(e.getId()==null) e.setCreatedBy(actor);
  e.setUpdatedBy(actor); e.setUpdatedAt(LocalDateTime.now());
  return e;
 }
 private void event(Jwt jwt,Long academy,String type,Long id) {
  audit.record("TRAINING_"+type,academy,AuditActorType.CUSTOMER,authorization.resolveUsername(jwt),null,null,null,"recordId="+id);
  customers.findById(academy).filter(this::academy).ifPresent(setup::evaluate);
 }
 private AcademySport sport(Long id,Long academy) {
  AcademySport s=sports.findById(id).orElseThrow(()->new ArenaOpsException(ErrorCode.RESOURCE_NOT_FOUND));
  if(!Objects.equals(s.getAcademyId(),academy)) throw new ArenaOpsException(ErrorCode.RESOURCE_NOT_FOUND);
  return s;
 }
 private AcademyStaff member(Long id,Long academy) {
  AcademyStaff s=staff.findById(id).orElseThrow(()->new ArenaOpsException(ErrorCode.RESOURCE_NOT_FOUND));
  if(!Objects.equals(s.getAcademyId(),academy)) throw new ArenaOpsException(ErrorCode.RESOURCE_NOT_FOUND);
  return s;
 }
 private TrainingBatch batch(Long id,Long academy) {
  TrainingBatch b=batches.findById(id).orElseThrow(()->new ArenaOpsException(ErrorCode.RESOURCE_NOT_FOUND));
  if(!Objects.equals(b.getAcademyId(),academy)) throw new ArenaOpsException(ErrorCode.RESOURCE_NOT_FOUND);
  return b;
 }
 private <T> Specification<T> filter(Long academy,String name,String search,Boolean active) {
  return (r,q,cb)-> {
   var predicates=new ArrayList<jakarta.persistence.criteria.Predicate>();
   if(academy!=null) predicates.add(cb.equal(r.get("academyId"),academy));
   if(search!=null && !search.isBlank()) predicates.add(cb.like(cb.lower(r.get(name)),"%"+search.trim().toLowerCase(Locale.ROOT).replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%",'\\'));
   if(active!=null) predicates.add(cb.equal(r.get("active"),active));
   return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
  };
 }
 private Pageable pagination(int page,int size,String sort) {
  if(page<0 || size<1 || size>100) throw new ArenaOpsException(ErrorCode.INVALID_REQUEST);
  return PageRequest.of(page,size,Sort.by(sort).and(Sort.by("id")));
 }
 public Map<String,Object> context(Jwt jwt) {
  AppUserEntity u=users.findByUsernameIgnoreCase(authorization.resolveUsername(jwt)).orElseThrow(()->new ArenaOpsException(ErrorCode.ACCESS_DENIED));
  if(!Boolean.TRUE.equals(u.getIsActive())||(u.getKeycloakSubject()!=null&&!u.getKeycloakSubject().equals(jwt.getSubject())))throw new ArenaOpsException(ErrorCode.ACCESS_DENIED);
  if(u.getCustomerId()==null)return Map.of("eligible",false);
  CustomerEntity c=customers.findBillingCustomerForUpdate(u.getCustomerId()).orElseThrow(()->new ArenaOpsException(ErrorCode.ACCESS_DENIED));
  access.assertCustomerCanUseApp(u);
  academyAuthorization.actor(jwt);
  if(!academy(c))return Map.of("eligible",false,"workspaceEligible",true,"timezone",Optional.ofNullable(c.getTrainingTimezone()).orElse("Asia/Kolkata"));
  return Map.of("eligible",true,"workspaceEligible",true,"name",Optional.ofNullable(c.getOrganizationName()).orElse(c.getCustomerName()),"timezone",c.getTrainingTimezone(),"skipped",c.getTrainingSetupSkipped(),"setupComplete","READY".equals(c.getSetupStatus()),"ownerManaged",c.getOwnerManaged(),"version",c.getVersion());
 }
 public Map<String,Object> settings(Jwt jwt,Settings request) {
  academyAuthorization.owner(jwt); CustomerEntity c=scope(jwt);
  if(!Objects.equals(c.getVersion(),request.version())) throw new ArenaOpsException(ErrorCode.CONFLICT);
  if(!ZoneId.getAvailableZoneIds().contains(request.timezone())) throw new ArenaOpsException(ErrorCode.INVALID_REQUEST);
  try { ZoneId.of(request.timezone()); } catch(DateTimeException e) { throw new ArenaOpsException(ErrorCode.INVALID_REQUEST); }
  c.setTrainingTimezone(request.timezone()); c.setTrainingSetupSkipped(request.skipped());
  customers.saveAndFlush(c); event(jwt,c.getId(),"SETTINGS",c.getId());
  return context(jwt);
 }
 public Page<SportCatalogue> catalogue(Jwt jwt,String search,int page,int size) {
  sportsScope(jwt); return catalogue.findAll(filter(null,"label",search,true),pagination(page,size,"label"));
 }
 public Page<AcademySport> sports(Jwt jwt,String search,Boolean active,int page,int size) {
  var actor=academyAuthorization.actor(jwt);Specification<AcademySport> spec=filter(actor.academyId(),"name",search,active);
  if(!actor.manager()) {if(!actor.roles().contains("INSTRUCTOR"))throw new ArenaOpsException(ErrorCode.ACCESS_DENIED);spec=spec.and((r,q,cb)->r.get("id").in(actor.membership().getSportIds()));}
  return sports.findAll(spec,pagination(page,size,"name"));
 }
 public AcademySport register(Jwt jwt,Sport request) {
  var customer=sportsScope(jwt);Long id=customer.getId();
  AcademySport s=activity(id,new Activity(request.catalogueCode(),request.customName(),request.active()));
  if(sports.findByAcademyIdOrderByName(id).stream().anyMatch(v->v.getRegistrationKey().equals(s.getRegistrationKey())||v.getName().equalsIgnoreCase(s.getName())))throw new ArenaOpsException(ErrorCode.TRAINING_SPORT_DUPLICATE);
  assertActiveSportLimit(customer,s.isActive()?1:0,null);
  capabilities(s,request.training(),request.sessions());
  sports.saveAndFlush(touched(s,jwt)); if(academy(customers.findById(id).orElseThrow()))event(jwt,id,"SPORT_REGISTERED",s.getId());else audit.record("TRAINING_SPORT_REGISTERED",id,AuditActorType.CUSTOMER,authorization.resolveUsername(jwt),null,null,null,"recordId="+s.getId());return s;
 }
 /** sportsScope holds the customer write lock across validation and all sport writes. */
 private void assertActiveSportLimit(CustomerEntity customer,long activating,Long excludedSport){
  if(activating>0 && "SINGLE".equalsIgnoreCase(customer.getSports()) &&
    activating+sports.findByAcademyIdOrderByName(customer.getId()).stream().filter(AcademySport::isActive).filter(s->!Objects.equals(s.getId(),excludedSport)).count()>1)
   throw new ArenaOpsException(ErrorCode.SINGLE_SPORT_LIMIT_EXCEEDED);
 }
 private void capabilities(AcademySport s,boolean training,boolean sessions) {
  s.setTraining(training); s.setSessions(sessions);
 }
 public AcademySport getSport(Jwt jwt,Long id){var actor=academyAuthorization.actor(jwt);var s=sport(id,actor.academyId());if(!actor.manager()&&(!actor.roles().contains("INSTRUCTOR")||!actor.membership().getSportIds().contains(id)))throw new ArenaOpsException(ErrorCode.ACCESS_DENIED);return s;}
 private String normalizedName(String name){if(name==null)throw new ArenaOpsException(ErrorCode.INVALID_REQUEST);String clean=name.strip().replaceAll("\\s+"," ");if(clean.length()<2||clean.length()>160||clean.codePoints().anyMatch(Character::isISOControl))throw new ArenaOpsException(ErrorCode.INVALID_REQUEST);return clean;}
 private AcademySport activity(Long academy,Activity request){
  var s=new AcademySport();s.setAcademyId(academy);s.setActive(!Boolean.FALSE.equals(request.active()));
  if("OTHER".equals(request.catalogueCode())){s.setName(normalizedName(request.customName()));if(catalogue.findAll().stream().anyMatch(v->v.getLabel().equalsIgnoreCase(s.getName())))throw new ArenaOpsException(ErrorCode.TRAINING_SPORT_DUPLICATE);s.setRegistrationKey("CUSTOM:"+s.getName().toLowerCase(Locale.ROOT));}
  else{var item=catalogue.findByCodeAndActiveTrue(request.catalogueCode()).orElseThrow(()->new ArenaOpsException(ErrorCode.INVALID_REQUEST));s.setCatalogueCode(item.getCode());s.setName(item.getLabel());s.setRegistrationKey(item.getCode());}
  return s;
 }
 public List<AcademySport> registerActivities(Jwt jwt,Activities request){
  var c=sportsScope(jwt);var prepared=new ArrayList<AcademySport>();var keys=new HashSet<String>();var names=new HashSet<String>();
  for(var existing:sports.findByAcademyIdOrderByName(c.getId())){keys.add(existing.getRegistrationKey());names.add(existing.getName().toLowerCase(Locale.ROOT));}
  var errors=new LinkedHashMap<String,String>();int index=0;
  for(var item:request.activities()){
   try{var s=activity(c.getId(),item);if(!keys.add(s.getRegistrationKey())||!names.add(s.getName().toLowerCase(Locale.ROOT)))throw new ArenaOpsException(ErrorCode.TRAINING_SPORT_DUPLICATE);prepared.add(s);}
   catch(ArenaOpsException ex){errors.put("activities["+index+"]",ex.getErrorCode()==ErrorCode.TRAINING_SPORT_DUPLICATE?"Already registered or duplicated in this selection: "+("OTHER".equals(item.catalogueCode())?item.customName():item.catalogueCode()):"Invalid activity code or custom name at selection "+(index+1));}index++;
  }
  if(!errors.isEmpty())throw new ActivitySelectionException(errors);
  assertActiveSportLimit(c,prepared.stream().filter(AcademySport::isActive).count(),null);
  for(var s:prepared)sports.saveAndFlush(touched(s,jwt));
  audit.record("TRAINING_ACTIVITIES_REGISTERED",c.getId(),AuditActorType.CUSTOMER,authorization.resolveUsername(jwt),null,null,null,"count="+prepared.size());
  if(academy(c))setup.evaluate(c);return prepared;
 }
 public AcademySport renameSport(Jwt jwt,Long id,Rename request){var c=sportsScope(jwt);var s=sport(id,c.getId());version(s,request.version());if(s.getCatalogueCode()!=null)throw new ArenaOpsException(ErrorCode.INVALID_REQUEST);var proposed=activity(c.getId(),new Activity("OTHER",request.name()));if(sports.findByAcademyIdOrderByName(c.getId()).stream().anyMatch(v->!Objects.equals(v.getId(),id)&&(v.getRegistrationKey().equals(proposed.getRegistrationKey())||v.getName().equalsIgnoreCase(proposed.getName()))))throw new ArenaOpsException(ErrorCode.TRAINING_SPORT_DUPLICATE);s.setName(proposed.getName());s.setRegistrationKey(proposed.getRegistrationKey());sports.saveAndFlush(touched(s,jwt));audit.record("TRAINING_ACTIVITY_RENAMED",c.getId(),AuditActorType.CUSTOMER,authorization.resolveUsername(jwt),null,null,null,"recordId="+id);return s;}
 private boolean ongoing(CustomerEntity c,TrainingBatch batch){
  if(!"ACTIVE".equals(batch.getStatus()))return false;
  var now=ZonedDateTime.now(ZoneId.of(c.getTrainingTimezone()));var today=now.toLocalDate();
  if(batch.getEndDate()!=null&&batch.getEndDate().isBefore(today))return false;
  var entries=schedules.findByBatchIdAndActiveTrueOrderByDayOfWeekAscStartTimeAsc(batch.getId());
  if(entries.isEmpty())return true; // Conservative handling of an inconsistent active recurring batch.
  return entries.stream().anyMatch(e->{var from=today;if(batch.getStartDate().isAfter(from))from=batch.getStartDate();if(e.getEffectiveFrom()!=null&&e.getEffectiveFrom().isAfter(from))from=e.getEffectiveFrom();var next=from.plusDays(Math.floorMod(e.getDayOfWeek()-from.getDayOfWeek().getValue(),7));if(next.equals(today)&&!e.getEndTime().isAfter(now.toLocalTime()))next=next.plusDays(7);return (batch.getEndDate()==null||!next.isAfter(batch.getEndDate()))&&(e.getEffectiveTo()==null||!next.isAfter(e.getEffectiveTo()));});
 }
 public AcademySport updateSport(Jwt jwt,Long id,SportUpdate request) {
  var c=sportsScope(jwt);Long academy=c.getId();AcademySport s=sport(id,academy);version(s,request.version());
  if(!s.isActive()&&request.active())assertActiveSportLimit(c,1,id);
  if((s.isActive()&&!request.active())||(s.isTraining()&&!request.training()))if(batches.findByAcademyIdOrderByName(academy).stream().anyMatch(b->Objects.equals(b.getSportId(),id)&&("DRAFT".equals(b.getStatus())||ongoing(c,b))))throw new ArenaOpsException(ErrorCode.TRAINING_DEPENDENCY_EXISTS);
  if((s.isActive()&&!request.active()||s.isSessions()&&!request.sessions())&&runningSessions!=null){
   // Paused sessions retain STARTED status and a pausedAt timestamp.
   if(runningSessions.existsByAcademyIdAndSportIdAndStatus(academy,id,"STARTED"))throw new ArenaOpsException(ErrorCode.SPORT_SESSION_ACTIVE);
   if(runningSessions.existsByAcademyIdAndSportIdAndStatus(academy,id,"RESERVED"))throw new ArenaOpsException(ErrorCode.SPORT_RESERVATION_EXISTS);
  }
  // Existing facility records are configuration, not bookings; preserve them when toggling capability/status.
  String previous="active="+s.isActive()+";training="+s.isTraining()+";sessions="+s.isSessions();
  capabilities(s,request.training(),request.sessions());s.setActive(request.active());sports.saveAndFlush(touched(s,jwt));
  String current="active="+s.isActive()+";training="+s.isTraining()+";sessions="+s.isSessions();
  audit.record("TRAINING_SPORT_UPDATED",academy,AuditActorType.CUSTOMER,authorization.resolveUsername(jwt),previous,current,null,"recordId="+id+";changedFields=training,sessions,active");if(academy(c))setup.evaluate(c);return s;
 }
 public Page<AcademyStaff> staff(Jwt jwt,String search,Boolean active,int page,int size) {
  return staff.findAll(filter(sportsScope(jwt).getId(),"name",search,active),pagination(page,size,"name"));
 }
 public AcademyStaff addStaff(Jwt jwt,Staff request) {
  Long academy=sportsScope(jwt).getId();
  // Registration is an academy personnel operation, never an identity operation.
  AcademyStaff m=new AcademyStaff(); m.setAcademyId(academy); m.setName(request.name().trim());
  m.setEmail(optionalEmail(request.email())); m.setPhone(request.phone()); m.setRole(request.role()); m.setActive(true);
  m.setDesignation(request.designation()==null?("COACH".equals(request.role())?"COACH":"GENERAL_STAFF"):request.designation());
  academyAuthorization.personnel(academyAuthorization.operations(jwt),m,m.getDesignation(),null);
  if("COACH".equals(m.getDesignation()))m.setRole("COACH");else m.setRole("STAFF");
  staff.saveAndFlush(touched(m,jwt)); event(jwt,academy,"STAFF_ADDED",m.getId()); return m;
 }
 private String optionalEmail(String email) {
  return email==null || email.isBlank()?null:email.trim();
 }
 // Keep old clients from invoking the unsafe, untracked invitation workflow.
 public void invite(Jwt jwt,Long id) {
  Long academy=sportsScope(jwt).getId(); member(id,academy);
  throw new ArenaOpsException(ErrorCode.STAFF_LOGIN_ACCESS_UNAVAILABLE);
 }
 public AcademyStaff updateStaff(Jwt jwt,Long id,StaffUpdate request) {
  Long academy=sportsScope(jwt).getId(); AcademyStaff m=member(id,academy);
  academyAuthorization.personnel(academyAuthorization.operations(jwt),m,request.designation(),null);version(m,request.version());
  if(!request.active() || (!"COACH".equals(request.role())&&!m.getApplicationRoles().contains("INSTRUCTOR"))) {
   if(batches.findByAcademyIdOrderByName(academy).stream().anyMatch(b->!"INACTIVE".equals(b.getStatus()) && b.getCoachIds().contains(id)))
    throw new ArenaOpsException(ErrorCode.TRAINING_DEPENDENCY_EXISTS);
   if(!request.active() && !m.getSportIds().isEmpty()) throw new ArenaOpsException(ErrorCode.TRAINING_DEPENDENCY_EXISTS);
   if(!"COACH".equals(request.role())&&!m.getApplicationRoles().contains("INSTRUCTOR")) m.getSportIds().clear();
  }
  if(m.getRegistrationRequestId()!=null&&m.getAppUserId()==null)throw new ArenaOpsException(ErrorCode.INVALID_STATE);
  // Existing identity links remain unchanged. Personnel edits must not grant realm roles.
  String email=optionalEmail(request.email());
  if(m.getAppUserId()!=null && !Objects.equals(m.getEmail(),email))
   throw new ArenaOpsException(ErrorCode.INVALID_REQUEST);
  m.setEmail(email);
  m.setName(request.name().trim()); m.setPhone(request.phone()); m.setRole(request.role()); m.setActive(request.active());
  if(request.designation()!=null)m.setDesignation(request.designation());
  staff.saveAndFlush(touched(m,jwt)); event(jwt,academy,"STAFF_UPDATED",id); return m;
 }
 public AcademyStaff assignSports(Jwt jwt,Long id,AssignSports request) {
  Long academy=sportsScope(jwt).getId(); AcademyStaff m=member(id,academy); version(m,request.version());
  if(!request.sportIds().isEmpty() && (!m.isActive() || !coaching(m))) throw new ArenaOpsException(ErrorCode.TRAINING_COACH_INVALID);
  for(Long sportId:request.sportIds()) { AcademySport s=sport(sportId,academy); if(!s.isActive()||!s.isTraining()) throw new ArenaOpsException(ErrorCode.TRAINING_COACH_INVALID); }
  if(batches.findByAcademyIdOrderByName(academy).stream().anyMatch(b->!"INACTIVE".equals(b.getStatus())&&b.getCoachIds().contains(id)&&!request.sportIds().contains(b.getSportId())))
   throw new ArenaOpsException(ErrorCode.TRAINING_DEPENDENCY_EXISTS);
  m.setSportIds(new HashSet<>(request.sportIds())); staff.saveAndFlush(touched(m,jwt)); event(jwt,academy,"COACH_SPORTS",id); return m;
 }
 public Page<TrainingBatch> batches(Jwt jwt,String search,Long sportId,String status,int page,int size) {
  var actor=academyAuthorization.actor(jwt);Long academy=actor.academyId(); Specification<TrainingBatch> spec=filter(academy,"name",search,null);
  if(!actor.manager()){if(!actor.roles().contains("INSTRUCTOR"))throw new ArenaOpsException(ErrorCode.ACCESS_DENIED);spec=spec.and((r,q,cb)->{q.distinct(true);return cb.equal(r.join("coachIds"),actor.membership().getId());});}
  if(sportId!=null) { sport(sportId,academy); spec=spec.and((r,q,cb)->cb.equal(r.get("sportId"),sportId)); }
  if(status!=null&&!status.isBlank()) spec=spec.and((r,q,cb)->cb.equal(r.get("status"),status));
  return batches.findAll(spec,pagination(page,size,"name"));
 }
 public Map<String,Object> detail(Jwt jwt,Long id) {
  var actor=academyAuthorization.actor(jwt);TrainingBatch b=batch(id,actor.academyId());
  if(!actor.manager()&&(!actor.roles().contains("INSTRUCTOR")||!b.getCoachIds().contains(actor.membership().getId())))throw new ArenaOpsException(ErrorCode.ACCESS_DENIED);
  return Map.of("batch",b,"schedules",schedules.findByBatchIdAndActiveTrueOrderByDayOfWeekAscStartTimeAsc(id));
 }
 public TrainingBatch saveBatch(Jwt jwt,Long id,Batch request) {
  Long academy=scope(jwt).getId(); TrainingBatch b=id==null?new TrainingBatch():batch(id,academy);
  if(id!=null) version(b,request.version());
  AcademySport s=sport(request.sportId(),academy); if(!"INACTIVE".equals(request.status()) && (!s.isActive()||!s.isTraining())) throw new ArenaOpsException(ErrorCode.INVALID_STATE);
  if(id!=null&&!Objects.equals(b.getSportId(),request.sportId())) throw new ArenaOpsException(ErrorCode.INVALID_REQUEST);
  if(request.endDate()!=null&&request.endDate().isBefore(request.startDate())) throw new ArenaOpsException(ErrorCode.INVALID_REQUEST);
  b.setAcademyId(academy); b.setSportId(s.getId()); b.setName(request.name().trim()); b.setCode(request.code().trim().toUpperCase(Locale.ROOT));
  b.setLevel(request.level()); b.setCapacity(request.capacity()); b.setStartDate(request.startDate()); b.setEndDate(request.endDate()); b.setStatus(request.status());
  List<BatchSchedule> entries=id==null?List.of():schedules.findByBatchIdAndActiveTrueOrderByDayOfWeekAscStartTimeAsc(id);
  validateBatch(b,entries); batches.saveAndFlush(touched(b,jwt)); event(jwt,academy,"BATCH_UPDATED",b.getId()); return b;
 }
 private void validateCoaches(TrainingBatch b) {
  for(Long id:b.getCoachIds()) {
   AcademyStaff m=member(id,b.getAcademyId());
   if(!m.isActive()||!coaching(m)||!m.getSportIds().contains(b.getSportId())) throw new ArenaOpsException(ErrorCode.TRAINING_COACH_INVALID);
  }
  if(b.getLeadCoachId()!=null&&!b.getCoachIds().contains(b.getLeadCoachId())) throw new ArenaOpsException(ErrorCode.TRAINING_COACH_INVALID);
 }
 private void validateBatch(TrainingBatch b,List<BatchSchedule> entries) {
  // Historical inactive batches can retain deactivated staff/sport references.
  if(!"INACTIVE".equals(b.getStatus())){var activity=sport(b.getSportId(),b.getAcademyId());if(!activity.isActive()||!activity.isTraining())throw new ArenaOpsException(ErrorCode.INVALID_STATE);validateCoaches(b);}
  entries.forEach(e->ScheduleRules.validate(e,b)); ScheduleRules.noInternalOverlap(entries);
  boolean ownerManaged=customers.findById(b.getAcademyId()).map(c->Boolean.TRUE.equals(c.getOwnerManaged())).orElse(false);
  if("ACTIVE".equals(b.getStatus()) && ((!ownerManaged&&b.getCoachIds().isEmpty())||entries.isEmpty())) throw new ArenaOpsException(ErrorCode.TRAINING_ACTIVATION_INCOMPLETE);
  if("INACTIVE".equals(b.getStatus())) return;
  for(TrainingBatch other:batches.findByAcademyIdOrderByName(b.getAcademyId())) {
   if(Objects.equals(other.getId(),b.getId())||!"ACTIVE".equals(other.getStatus())||(Collections.disjoint(b.getCoachIds(),other.getCoachIds())&&!(ownerManaged&&b.getCoachIds().isEmpty()&&other.getCoachIds().isEmpty()))) continue;
   for(BatchSchedule left:entries) for(BatchSchedule right:schedules.findByBatchIdAndActiveTrueOrderByDayOfWeekAscStartTimeAsc(other.getId()))
    if(ScheduleRules.overlaps(left,right)) throw new ArenaOpsException(ErrorCode.TRAINING_SCHEDULE_CONFLICT);
  }
 }
 public TrainingBatch assignCoaches(Jwt jwt,Long id,Coaches request) {
  Long academy=scope(jwt).getId(); TrainingBatch b=batch(id,academy); version(b,request.version());
  b.setCoachIds(new HashSet<>(request.coachIds())); b.setLeadCoachId(request.leadCoachId());
  validateCoaches(b); validateBatch(b,schedules.findByBatchIdAndActiveTrueOrderByDayOfWeekAscStartTimeAsc(id));
  batches.saveAndFlush(touched(b,jwt)); event(jwt,academy,"BATCH_COACHES",id); return b;
 }
 public Map<String,Object> replaceSchedules(Jwt jwt,Long id,Schedules request) {
  Long academy=scope(jwt).getId(); TrainingBatch b=batch(id,academy); version(b,request.version());
  List<BatchSchedule> next=new ArrayList<>();
  for(Weekly w:request.schedules()) { BatchSchedule s=new BatchSchedule(); s.setBatchId(id); s.setDayOfWeek(w.dayOfWeek());
   s.setStartTime(w.startTime()); s.setEndTime(w.endTime()); s.setEffectiveFrom(w.effectiveFrom()); s.setEffectiveTo(w.effectiveTo()); s.setActive(true); next.add(s); }
  validateBatch(b,next);
  for(BatchSchedule previous:schedules.findByBatchIdAndActiveTrueOrderByDayOfWeekAscStartTimeAsc(id)) { previous.setActive(false); schedules.save(touched(previous,jwt)); }
  for(BatchSchedule entry:next) schedules.save(touched(entry,jwt));
  batches.saveAndFlush(touched(b,jwt)); event(jwt,academy,"BATCH_SCHEDULES",id); return detail(jwt,id);
 }
}
