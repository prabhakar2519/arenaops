package com.arena.core.checkin;

import static com.arena.core.checkin.FacilityCheckinContracts.*;
import com.arena.core.training.*;
import com.arena.core.sessions.*;
import com.arena.core.facility.FacilityService;
import com.arena.core.repository.CustomerRepository;
import com.arena.core.service.CustomerAccessService;
import com.arena.core.exception.*;
import java.time.*;
import java.util.*;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.oauth2.jwt.Jwt;

@Service @Transactional @RequiredArgsConstructor
public class FacilityCheckinService {
 private final AcademyAuthorization authorization;
 private final FacilityService facilities;
 private final AcademyPlayAreaRepository areas;
 private final AcademySportRepository sports;
 private final FacilityQrChallengeRepository challenges;
 private final FacilityCustomerCheckinRepository checkins;
 private final RunningSessionService lifecycle;
 private final CustomerRepository customers;
 private final CustomerAccessService access;
 private static final SecureRandom RANDOM=new SecureRandom();
 private static final Duration CHALLENGE_TTL=Duration.ofMinutes(5),PENDING_TTL=Duration.ofMinutes(15);

 public Challenge issue(Jwt jwt,Long facilityId){
  var a=authorization.actor(jwt);var f=facilities.requireEligibleLocked(jwt,a.academyId(),facilityId);operator(a,f.getSportId());snooker(f);
  byte[] bytes=new byte[32];RANDOM.nextBytes(bytes);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  var q=new FacilityQrChallenge();q.setAcademyId(a.academyId());q.setFacilityId(f.getId());q.setTokenHash(hash(token));q.setExpiresAt(Instant.now().plus(CHALLENGE_TTL));challenges.save(q);
  return new Challenge(token,label(f),q.getExpiresAt());
 }
 public PublicInfo inspect(String token){var q=challenge(token);return new PublicInfo(label(publicFacility(q)),q.getExpiresAt());}
 public Receipt submit(Submission request){
  var q=challenge(request.token());publicFacility(q);
  String name=Normalizer.normalize(request.name().strip(),Normalizer.Form.NFC);
  String mobile=request.mobile().replaceAll("[\\s()\\-]","");
  if(!request.privacyAccepted()||name.isBlank()||name.length()>100||name.codePoints().anyMatch(Character::isISOControl)||!mobile.matches("\\+[1-9][0-9]{7,14}"))throw new ArenaOpsException(ErrorCode.INVALID_REQUEST);
  var now=Instant.now();if(!q.getExpiresAt().isAfter(now))throw new ArenaOpsException(ErrorCode.QR_CHECKIN_UNAVAILABLE);var c=new FacilityCustomerCheckin();c.setAcademyId(q.getAcademyId());c.setFacilityId(q.getFacilityId());c.setChallengeId(q.getId());c.setCustomerName(name);c.setMobile(mobile);c.setStatus("PENDING");c.setCreatedAt(now);c.setExpiresAt(now.plus(PENDING_TTL));
  q.setUsedAt(now);challenges.save(q);checkins.saveAndFlush(c);
  return new Receipt("PENDING",c.getExpiresAt());
 }
 public List<Pending> pending(Jwt jwt){var a=authorization.actor(jwt);return checkins.findTop100ByAcademyIdAndStatusAndExpiresAtAfterOrderByCreatedAtAsc(a.academyId(),"PENDING",Instant.now()).stream().filter(c->canOperate(a,area(c).getSportId())).map(c->new Pending(c.getId(),c.getFacilityId(),label(area(c)),c.getCustomerName(),c.getMobile(),false,c.getCreatedAt(),c.getExpiresAt())).toList();}
 public Confirmation confirm(Jwt jwt,Long id,Confirm request){
  var a=authorization.actor(jwt);
  // Existing tenant write lock precedes the check-in/facility locks, just as manual start.
  var c=checkins.locked(a.academyId(),id).orElseThrow(()->new ArenaOpsException(ErrorCode.RESOURCE_NOT_FOUND));
  operator(a,area(c).getSportId());if(!"PENDING".equals(c.getStatus())||!c.getExpiresAt().isAfter(Instant.now()))throw new ArenaOpsException(ErrorCode.QR_CHECKIN_UNAVAILABLE);
  var session=lifecycle.start(jwt,new RunningSessionContracts.Start(c.getFacilityId(),c.getCustomerName(),request.plannedEndAt()));
  if(!c.getExpiresAt().isAfter(Instant.now()))throw new ArenaOpsException(ErrorCode.QR_CHECKIN_UNAVAILABLE);
  c.setSessionId(session.id());c.setStatus("CONFIRMED");c.setConfirmedAt(Instant.now());c.setConfirmedBy(a.user().getUsername());checkins.saveAndFlush(c);
  return new Confirmation(session.id(),"STARTED");
 }
 public void cancel(Jwt jwt,Long id){var a=authorization.actor(jwt);var c=checkins.locked(a.academyId(),id).orElseThrow(()->new ArenaOpsException(ErrorCode.RESOURCE_NOT_FOUND));operator(a,area(c).getSportId());if(!"PENDING".equals(c.getStatus()))throw new ArenaOpsException(ErrorCode.CONFLICT);c.setStatus("CANCELLED");c.setCustomerName(null);c.setMobile(null);checkins.save(c);}
 public void cleanup(){var now=Instant.now();checkins.expire(now);checkins.scrub(now.minus(Duration.ofHours(24)));checkins.purge(now.minus(Duration.ofHours(24)));challenges.purge(now.minus(Duration.ofHours(24)));}
 private FacilityQrChallenge challenge(String token){if(token==null||!token.matches("[A-Za-z0-9_-]{43}"))throw new ArenaOpsException(ErrorCode.QR_CHECKIN_UNAVAILABLE);var q=challenges.locked(hash(token)).orElseThrow(()->new ArenaOpsException(ErrorCode.QR_CHECKIN_UNAVAILABLE));if(q.getUsedAt()!=null||!q.getExpiresAt().isAfter(Instant.now()))throw new ArenaOpsException(ErrorCode.QR_CHECKIN_UNAVAILABLE);return q;}
 private AcademyPlayArea publicFacility(FacilityQrChallenge q){var f=areas.findById(q.getFacilityId()).filter(v->Objects.equals(v.getAcademyId(),q.getAcademyId())).orElseThrow(()->new ArenaOpsException(ErrorCode.QR_CHECKIN_UNAVAILABLE));
  if(!f.isActive()||f.getHourlyRate()==null||f.getHourlyRate().signum()<=0||!access.isCustomerUsable(customers.findById(q.getAcademyId()).orElse(null)))throw new ArenaOpsException(ErrorCode.QR_CHECKIN_UNAVAILABLE);snooker(f);return f;}
 private AcademyPlayArea area(FacilityCustomerCheckin c){return areas.findById(c.getFacilityId()).filter(f->Objects.equals(f.getAcademyId(),c.getAcademyId())).orElseThrow(()->new ArenaOpsException(ErrorCode.RESOURCE_NOT_FOUND));}
 private void snooker(AcademyPlayArea f){var sport=sports.findById(f.getSportId()).filter(s->Objects.equals(s.getAcademyId(),f.getAcademyId())&&s.isActive()&&s.isSessions()&&"SNOOKER".equals(s.getCatalogueCode())).orElseThrow(()->new ArenaOpsException(ErrorCode.QR_CHECKIN_UNAVAILABLE));}
 private boolean canOperate(AcademyAuthorization.Actor a,Long sport){return a.manager()||a.roles().contains("STAFF")||(a.membership()!=null&&(a.roles().contains("INSTRUCTOR")||"COACH".equals(a.membership().getRole()))&&a.membership().getSportIds().contains(sport));}
 private void operator(AcademyAuthorization.Actor a,Long sport){if(!canOperate(a,sport))throw new ArenaOpsException(ErrorCode.ACCESS_DENIED);}
 private String label(AcademyPlayArea f){return f.getDisplayName()==null?f.getCode():f.getDisplayName();}
 private String hash(String token){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
