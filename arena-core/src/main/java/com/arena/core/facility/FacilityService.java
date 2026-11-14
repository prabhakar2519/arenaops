package com.arena.core.facility;

import com.arena.core.training.*;
import com.arena.core.sessions.AcademyRunningSessionRepository;
import com.arena.core.exception.*;
import com.arena.core.service.AuditService;
import com.arena.core.enums.AuditActorType;
import com.arena.core.repository.AuditEventRepository;
import com.fasterxml.jackson.databind.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.beans.factory.annotation.Value;
import java.util.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import static com.arena.core.facility.FacilityContracts.*;

@Service @lombok.RequiredArgsConstructor(onConstructor_=@org.springframework.beans.factory.annotation.Autowired) @Transactional
public class FacilityService {
 private final AcademyAuthorization authorization;private final AcademySportRepository sports;private final AcademyPlayAreaRepository facilities;private final FacilityLedger ledger;private final AuditService audit;private final ObjectMapper json;private final AuditEventRepository auditEvents;private final AcademySetupService setup;private final com.arena.core.repository.CustomerRepository customers;private final AcademyRunningSessionRepository runningSessions;
 @Value("${arena.facilities.max-per-type:50}") private int maxPerType=50;
 @Value("${arena.facilities.max-bulk:200}") private int maxBulk=200;
 public FacilityService(AcademyAuthorization authorization,AcademySportRepository sports,AcademyPlayAreaRepository facilities,FacilityLedger ledger,AuditService audit,ObjectMapper json,AuditEventRepository auditEvents,AcademySetupService setup,com.arena.core.repository.CustomerRepository customers){this(authorization,sports,facilities,ledger,audit,json,auditEvents,setup,customers,null);}
 private static final Map<String,Set<String>> TYPES=Map.of("SNOOKER",Set.of("ENGLISH","FRENCH","POOL","SEMI_ENGLISH"),"BADMINTON",Set.of("COURT"));
 private static final Map<String,String> PREFIX=Map.of("ENGLISH","E","FRENCH","F","POOL","P","SEMI_ENGLISH","SE","COURT","C");
 @jakarta.annotation.PostConstruct void validateLimits(){if(maxPerType<1||maxPerType>1000||maxBulk<1||maxBulk>1000)throw new IllegalStateException("Facility bulk limits must be between 1 and 1000");}
 public JsonNode parse(byte[] body){try{return json.reader().with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readTree(body);}catch(java.io.IOException ex){throw invalid("request","Provide a valid JSON object.");}}
 public Long authorize(Jwt jwt,Long academy){Long actual=authorization.manage(jwt).academyId();if(!actual.equals(academy))throw new ArenaOpsException(ErrorCode.ACCESS_DENIED);return actual;}
 public Map<String,Object> context(Jwt jwt){var actor=authorization.manage(jwt);return Map.of("academyId",actor.academyId(),"sports",sports.findByAcademyIdOrderByName(actor.academyId()),"limits",Map.of("maxPerType",maxPerType,"maxBulk",maxBulk));}
 private AcademySport sport(Long academy,Long id){return sports.findById(id).filter(s->Objects.equals(s.getAcademyId(),academy)).orElseThrow(()->new FacilityFailure(ErrorCode.FACILITY_NOT_FOUND,"SPORT_NOT_FOUND"));}
 public static boolean supported(AcademySport sport){return TYPES.containsKey(Objects.toString(sport.getCatalogueCode(),""));}
 private void capable(AcademySport sport){if(!supported(sport))throw new FacilityFailure(ErrorCode.FACILITY_UNSUPPORTED,"UNSUPPORTED_SPORT");if(!sport.isActive()||!sport.isSessions())throw new FacilityFailure(ErrorCode.FACILITY_CAPABILITY_DISABLED,"CAPABILITY_DISABLED");}
 public Result list(Jwt jwt,Long academy,Long sportId){authorize(jwt,academy);var sport=sport(academy,sportId);if(!supported(sport))throw new FacilityFailure(ErrorCode.FACILITY_UNSUPPORTED,"UNSUPPORTED_SPORT");return new Result(facilities.findByAcademyIdAndSportIdOrderById(academy,sportId).stream().map(FacilityContracts::view).toList());}
 private FacilityFailure invalid(String field,String message){return new FacilityFailure(ErrorCode.FACILITY_INVALID_REQUEST,"INVALID_FIELDS",Map.of(field,message),List.of());}
 private void keys(JsonNode node,Set<String> allowed,String field){if(node==null||!node.isObject())throw invalid(field,"Expected an object.");node.fieldNames().forEachRemaining(key->{if(!allowed.contains(key))throw invalid(field+"."+key,"This field is not accepted.");});}
 public Result create(Jwt jwt,Long academy,Long sportId,UUID key,JsonNode body){
  authorize(jwt,academy);var sport=sport(academy,sportId);keys(body,Set.of("facilities"),"request");var items=body.get("facilities");if(items==null||!items.isArray()||items.isEmpty()||items.size()>5)throw invalid("facilities","Select at least one facility type.");
  var counts=new TreeMap<String,Integer>();long total=0;
  for(int i=0;i<items.size();i++){var item=items.get(i);String field="facilities["+i+"]";keys(item,Set.of("facilityType","count"),field);String type=item.path("facilityType").asText();var count=item.path("count");if(!TYPES.getOrDefault(Objects.toString(sport.getCatalogueCode(),""),Set.of()).contains(type))throw new FacilityFailure(ErrorCode.FACILITY_UNSUPPORTED,"UNSUPPORTED_TYPE",Map.of(field+".facilityType","Choose a supported type for this sport."),List.of());if(!count.isIntegralNumber()||!count.canConvertToInt()||count.intValue()<1||count.intValue()>maxPerType)throw invalid(field+".count","Enter a positive whole quantity up to "+maxPerType+".");if(counts.put(type,count.intValue())!=null)throw invalid(field+".facilityType","Duplicate facility type.");total+=count.intValue();}
  if(total>maxBulk)throw invalid("facilities","The bulk limit is "+maxBulk+" facilities.");
  String fingerprint=hash(academy+":"+sportId+":"+counts);var replay=ledger.replay(academy,key);
  if(replay.isPresent()){if(!fingerprint.equals(replay.get().fingerprint()))throw new FacilityFailure(ErrorCode.FACILITY_IDEMPOTENCY_CONFLICT,"IDEMPOTENCY_PAYLOAD_CHANGED");try{return json.readValue(replay.get().response(),Result.class);}catch(Exception ex){throw new IllegalStateException("Stored facility result cannot be decoded");}}
  capable(sport);var created=new ArrayList<View>();
  for(var item:counts.entrySet()){String type=item.getKey(),prefix=PREFIX.get(type);long first=ledger.allocate(academy,sportId,type,prefix,item.getValue());for(int i=0;i<item.getValue();i++){var f=new AcademyPlayArea();f.setAcademyId(academy);f.setSportId(sportId);f.setFacilityType(type);f.setCode(prefix+(first+i));f.setName(f.getCode());f.setKind(type.equals("COURT")?"COURT":"TABLE");f.setActive(true);f.setCreatedBy(jwt.getSubject());f.setUpdatedBy(jwt.getSubject());facilities.saveAndFlush(f);event(jwt,f,"CREATED",null,state(f));created.add(view(f));}}
  var result=new Result(created);try{ledger.remember(academy,sportId,key,fingerprint,json.writeValueAsString(result));}catch(com.fasterxml.jackson.core.JsonProcessingException ex){throw new IllegalStateException("Facility result cannot be encoded");}evaluateSetup(academy);return result;
 }
 private static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception ex){throw new IllegalStateException(ex);}}
 private BigDecimal money(JsonNode value,String field){if(value.isNull())return null;if(!value.isNumber())throw invalid(field,"Enter a positive decimal amount or null.");BigDecimal amount=value.decimalValue();if(amount.signum()<=0||amount.stripTrailingZeros().scale()>2||amount.compareTo(new BigDecimal("9999999999.99"))>0)throw invalid(field,"Use a positive amount up to 9,999,999,999.99 with at most two decimal places.");return amount.setScale(2);}
 public Result patch(Jwt jwt,Long academy,JsonNode body){
  authorize(jwt,academy);keys(body,Set.of("facilities"),"request");var items=body.path("facilities");if(!items.isArray()||items.isEmpty()||items.size()>maxBulk)throw invalid("facilities","Provide between 1 and "+maxBulk+" changed records.");
  var requests=new TreeMap<Long,JsonNode>();for(int i=0;i<items.size();i++){var item=items.get(i);keys(item,Set.of("facilityId","version","displayName","hourlyRate","frameRate","active"),"facilities["+i+"]");if(!item.path("facilityId").isIntegralNumber()||!item.path("facilityId").canConvertToLong()||item.path("facilityId").longValue()<1||!item.path("version").isIntegralNumber()||!item.path("version").canConvertToLong()||item.path("version").longValue()<0)throw invalid("facilities["+i+"]","Facility ID and non-negative current version are required.");if(item.size()<=2)throw invalid("facilities["+i+"]","Include at least one changed configuration field.");if(requests.put(item.get("facilityId").longValue(),item)!=null)throw invalid("facilities","Duplicate facility ID.");}
  var locked=new LinkedHashMap<Long,AcademyPlayArea>();var conflicts=new ArrayList<Map<String,Object>>();
  for(var item:requests.entrySet()){var f=facilities.locked(academy,item.getKey()).orElseThrow(()->new FacilityFailure(ErrorCode.FACILITY_NOT_FOUND,"FACILITY_NOT_FOUND"));if(!Objects.equals(f.getVersion(),item.getValue().get("version").longValue()))conflicts.add(Map.of("facilityId",f.getId(),"code",f.getCode(),"submittedVersion",item.getValue().get("version").longValue(),"currentVersion",f.getVersion()));locked.put(f.getId(),f);}
  if(!conflicts.isEmpty())throw new FacilityFailure(ErrorCode.FACILITY_VERSION_CONFLICT,"STALE_VERSION",Map.of(),conflicts);
  var result=new ArrayList<View>();for(var item:requests.entrySet()){var f=locked.get(item.getKey());var input=item.getValue();var s=sport(academy,f.getSportId());if(f.getFacilityType()==null)throw invalid("facilityId","This legacy play area requires an explicit type-mapping review before conversion.");
   // Inactive parent sports are strictly read-only; disabled facility capability retains safe deactivation.
   if(!s.isActive())capable(s);
   boolean deactivationOnly=input.has("active")&&input.get("active").isBoolean()&&!input.get("active").booleanValue()&&!input.has("hourlyRate")&&!input.has("frameRate")&&!input.has("displayName");
   if(!deactivationOnly)capable(s);
   String previous=state(f);BigDecimal hourly=input.has("hourlyRate")?money(input.get("hourlyRate"),"facilities["+f.getId()+"].hourlyRate"):f.getHourlyRate();BigDecimal frame=input.has("frameRate")?money(input.get("frameRate"),"facilities["+f.getId()+"].frameRate"):f.getFrameRate();if("COURT".equals(f.getFacilityType())&&frame!=null)throw invalid("facilities["+f.getId()+"].frameRate","Courts support hourly pricing only.");
   if(input.has("displayName")){var name=input.get("displayName");if(!name.isNull()&&(!name.isTextual()||name.textValue().strip().length()>160))throw invalid("facilities["+f.getId()+"].displayName","Use an optional name up to 160 characters.");f.setDisplayName(name.isNull()||name.asText().isBlank()?null:name.asText().strip());}
   if(input.has("active")){if(!input.get("active").isBoolean())throw invalid("active","Active must be true or false.");if(f.isActive()&&!input.get("active").booleanValue())validateDeactivation(f);f.setActive(input.get("active").booleanValue());}
   f.setHourlyRate(hourly);f.setFrameRate(frame);if(previous.equals(state(f))){result.add(view(f));continue;}f.setName(f.getDisplayName()==null?f.getCode():f.getDisplayName());f.setUpdatedBy(jwt.getSubject());facilities.saveAndFlush(f);event(jwt,f,"UPDATED",previous,state(f));result.add(view(f));}
  evaluateSetup(academy);return new Result(result);
 }
 /** Caller holds the facility row lock also acquired by running-session start. */
 protected void validateDeactivation(AcademyPlayArea f){if(runningSessions!=null&&(runningSessions.existsByAcademyIdAndFacilityIdAndStatus(f.getAcademyId(),f.getId(),"STARTED")||runningSessions.existsByAcademyIdAndFacilityIdAndStatus(f.getAcademyId(),f.getId(),"RESERVED")))throw new FacilityFailure(ErrorCode.FACILITY_SESSION_ACTIVE,"ACTIVE_SESSION");}
 /** Operational callers join their transaction so the facility row lock survives session insertion. */
 @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
 public AcademyPlayArea requireEligibleLocked(Jwt jwt,Long academy,Long id){var actor=authorization.actor(jwt);if(!Objects.equals(actor.academyId(),academy))throw new ArenaOpsException(ErrorCode.ACCESS_DENIED);var f=facilities.locked(academy,id).orElseThrow(()->new FacilityFailure(ErrorCode.FACILITY_NOT_FOUND,"FACILITY_NOT_FOUND"));if(!actor.manager()&&!actor.roles().contains("STAFF")&&!(actor.membership()!=null&&(actor.roles().contains("INSTRUCTOR")||"COACH".equals(actor.membership().getRole()))&&actor.membership().getSportIds().contains(f.getSportId())))throw new ArenaOpsException(ErrorCode.ACCESS_DENIED);capable(sport(academy,f.getSportId()));if(!f.isActive()||!configured(f))throw new FacilityFailure(ErrorCode.FACILITY_NOT_ELIGIBLE,"INCOMPLETE_OR_INACTIVE");return f;}
 public List<com.arena.core.entity.AuditEventEntity> history(Jwt jwt,Long academy,Long id){authorize(jwt,academy);facilities.locked(academy,id).orElseThrow(()->new FacilityFailure(ErrorCode.FACILITY_NOT_FOUND,"FACILITY_NOT_FOUND"));return auditEvents.findTop200ByCustomerIdAndMetadataAndEventTypeStartingWithOrderByOccurredAtDescIdDesc(academy,"facilityId="+id,"FACILITY_");}
 private void evaluateSetup(Long academy){customers.findById(academy).filter(c->"ACADEMY".equalsIgnoreCase(c.getOrganizationType())||"ACADEMY".equalsIgnoreCase(c.getSports())).ifPresent(setup::evaluate);}
 private String state(AcademyPlayArea f){var value=json.createObjectNode();value.put("displayName",f.getDisplayName());value.put("hourlyRate",f.getHourlyRate());value.put("frameRate",f.getFrameRate());value.put("active",f.isActive());return value.toString();}
 private void event(Jwt jwt,AcademyPlayArea f,String action,String previous,String current){audit.record("FACILITY_"+action,f.getAcademyId(),AuditActorType.CUSTOMER,jwt.getSubject(),previous,current,null,"facilityId="+f.getId());}
}
