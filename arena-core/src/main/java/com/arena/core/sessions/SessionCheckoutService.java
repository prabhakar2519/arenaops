package com.arena.core.sessions;
import static com.arena.core.sessions.SessionCheckoutContracts.*;
import com.arena.core.training.*;
import com.arena.core.billing.*;
import com.arena.core.repository.CustomerRepository;
import com.arena.core.service.AuditService;
import com.arena.core.enums.AuditActorType;
import com.arena.core.exception.*;
import java.math.*;
import java.time.*;
import java.util.*;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import lombok.RequiredArgsConstructor;

@Service @Transactional @RequiredArgsConstructor
public class SessionCheckoutService {
 private final AcademyAuthorization authorization;
 private final AcademyRunningSessionRepository sessions;
 private final SessionStopQuoteRepository quotes;
 private final SessionDiscountRequestRepository requests;
 private final AcademyStaffRepository staff;
 private final AcademyPaymentRepository payments;
 private final AcademySportRepository sports;
 private final CustomerRepository customers;
 private final AuditService audit;
 @Value("${arena.sessions.stop-quote-seconds:120}") private long quoteSeconds=120;
 private static final SecureRandom RANDOM=new SecureRandom();
 private BigDecimal zero(BigDecimal v){return v==null?BigDecimal.ZERO:v;}
 private long version(AcademyRunningSession s){return s.getVersion()==null?0:s.getVersion();}
 private ZoneId zone(Long academy){return customers.findById(academy).map(c->c.getTrainingTimezone()).filter(v->v!=null&&!v.isBlank()).map(v->{try{return ZoneId.of(v);}catch(Exception e){return ZoneId.of("Asia/Kolkata");}}).orElse(ZoneId.of("Asia/Kolkata"));}
 private LocalDateTime now(Long academy){return LocalDateTime.now(zone(academy));}
 public boolean financial(AcademyAuthorization.Actor a){return a.operations();}
 public boolean approver(AcademyAuthorization.Actor a){return a.owner()||(a.operations()&&a.membership()!=null&&a.membership().isDiscountApprovalAllowed());}
 public Authority authority(AcademyAuthorization.Actor a){
  if(a.owner())return new Authority(true,true,true,null,null,null);
  var m=a.membership();if(m==null)return new Authority(false,false,false,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO);
  BigDecimal remaining=null;
  if(m.getDailyDiscountAllowance()!=null){var day=now(a.academyId()).toLocalDate().atStartOfDay();remaining=m.getDailyDiscountAllowance().subtract(zero(requests.dailyApproved(a.academyId(),a.user().getId(),day,day.plusDays(1)))).max(BigDecimal.ZERO);}
  return new Authority(m.isDiscountEnabled(),approver(a),financial(a),zero(m.getMaxDiscountPercent()),zero(m.getMaxDiscountAmount()),remaining);
 }
 public Authority capabilities(org.springframework.security.oauth2.jwt.Jwt jwt){return authority(authorization.actor(jwt));}
 public AcademyStaff policy(org.springframework.security.oauth2.jwt.Jwt jwt,Long id,Policy p){
  var a=authorization.owner(jwt);var m=staff.findById(id).filter(s->Objects.equals(s.getAcademyId(),a.academyId())).orElseThrow(()->new ArenaOpsException(ErrorCode.RESOURCE_NOT_FOUND));
  if(!Objects.equals(m.getVersion(),p.version()))throw new ArenaOpsException(ErrorCode.CONFLICT);
  if(p.canApprove()&&(!m.getApplicationRoles().contains("MANAGER")||!"FULL_OPERATIONS".equals(m.getManagerAccess())))throw new ArenaOpsException(ErrorCode.INVALID_REQUEST);
  m.setDiscountEnabled(p.enabled());m.setMaxDiscountPercent(p.maxPercent());m.setMaxDiscountAmount(p.maxAmount());m.setDailyDiscountAllowance(p.dailyAllowance());m.setDiscountApprovalAllowed(p.canApprove());
  audit.record("SESSION_DISCOUNT_POLICY_CHANGED",a.academyId(),AuditActorType.CUSTOMER,a.user().getUsername(),null,null,null,"staffId="+id+";"+p);
  return staff.saveAndFlush(m);
 }
 public Quote issue(AcademyAuthorization.Actor a,AcademyRunningSession s,LocalDateTime captured,long seconds,long paused,BigDecimal bill){
  byte[] bytes=new byte[32];RANDOM.nextBytes(bytes);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  quotes.invalidateExisting(a.academyId(),s.getId(),a.user().getId(),captured);
  var q=new SessionStopQuote();q.setTokenHash(hash(token));q.setAcademyId(a.academyId());q.setSessionId(s.getId());q.setActorId(a.user().getId());q.setSessionVersion(version(s));q.setCapturedAt(captured);q.setExpiresAt(captured.plusSeconds(quoteSeconds));q.setBillableSeconds(seconds);q.setPausedSeconds(paused);q.setOriginalAmount(bill);quotes.save(q);
  return new Quote(token,s.getId(),captured,q.getExpiresAt(),seconds,paused,bill,authority(a));
 }
 public SessionStopQuote consume(AcademyAuthorization.Actor a,AcademyRunningSession s,String token,LocalDateTime now){
  var q=ownedQuote(a,s,token);
  if(q.getConsumedAt()!=null||!q.getExpiresAt().isAfter(now)||q.getSessionVersion()!=version(s)||!"STARTED".equals(s.getStatus()))throw new ArenaOpsException(ErrorCode.SESSION_QUOTE_STALE);
  q.setConsumedAt(now);quotes.save(q);return q;
 }
 public void cancel(AcademyAuthorization.Actor a,AcademyRunningSession s,String token){var q=ownedQuote(a,s,token);if(q.getConsumedAt()==null){q.setConsumedAt(now(a.academyId()));quotes.save(q);}}
 private SessionStopQuote ownedQuote(AcademyAuthorization.Actor a,AcademyRunningSession s,String token){
  if(token==null||!token.matches("[A-Za-z0-9_-]{43}"))throw new ArenaOpsException(ErrorCode.INVALID_REQUEST);
  return quotes.findByTokenHashAndAcademyId(hash(token),a.academyId()).filter(q->Objects.equals(q.getSessionId(),s.getId())&&Objects.equals(q.getActorId(),a.user().getId())).orElseThrow(()->new ArenaOpsException(ErrorCode.SESSION_QUOTE_STALE));
 }
 private String hash(String token){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
 public BigDecimal money(BigDecimal amount,String sport){
  amount=zero(amount);if(amount.signum()<0||amount.precision()-amount.scale()>10||amount.scale()>2||("SNOOKER".equalsIgnoreCase(sport)&&amount.stripTrailingZeros().scale()>0))throw new ArenaOpsException(ErrorCode.INVALID_REQUEST);
  return amount.setScale("SNOOKER".equalsIgnoreCase(sport)?0:2,RoundingMode.UNNECESSARY);
 }
 private boolean auto(Authority p,BigDecimal amount,BigDecimal gross){return p.discountEnabled()&&(p.maxPercent()==null||amount.multiply(BigDecimal.valueOf(100)).compareTo(gross.multiply(p.maxPercent()))<=0)&&(p.maxAmount()==null||amount.compareTo(p.maxAmount())<=0)&&(p.dailyRemaining()==null||amount.compareTo(p.dailyRemaining())<=0);}
 public void discount(AcademyAuthorization.Actor a,AcademyRunningSession s,BigDecimal amount,String reason,BigDecimal collected){
  amount=money(amount,"SNOOKER");if(amount.signum()==0)return;
  if(!"SNOOKER".equalsIgnoreCase(sports.findById(s.getSportId()).orElseThrow().getCatalogueCode()))throw new ArenaOpsException(ErrorCode.INVALID_REQUEST);
  if(reason==null||reason.isBlank()||reason.length()>500||amount.compareTo(zero(s.getOriginalAmount()))>0)throw new ArenaOpsException(ErrorCode.INVALID_REQUEST);
  if("PENDING".equals(s.getDiscountStatus())||zero(s.getApprovedDiscount()).signum()>0)throw new ArenaOpsException(ErrorCode.CONFLICT);
  if(amount.compareTo(zero(s.getOriginalAmount()).subtract(zero(collected)))>0)throw new ArenaOpsException(ErrorCode.INVALID_REQUEST);
  var p=authority(a);boolean approved=auto(p,amount,s.getOriginalAmount());var r=new SessionDiscountRequest();r.setAcademyId(a.academyId());r.setSessionId(s.getId());r.setAmount(amount);r.setReason(reason.strip());r.setStatus(approved?"APPROVED":"PENDING");r.setRequestedById(a.user().getId());r.setRequestedBy(a.user().getUsername());r.setStaffId(a.membership()==null?null:a.membership().getId());r.setRequestedAt(now(a.academyId()));r.setPolicySnapshot(p.toString()+";membershipVersion="+(a.membership()==null?"OWNER":a.membership().getVersion()));
  if(approved){r.setDecidedById(a.user().getId());r.setDecidedBy(a.user().getUsername());r.setDecidedAt(r.getRequestedAt());r.setDecisionReason("Within configured authority");}
  requests.saveAndFlush(r);s.setDiscountRequestId(r.getId());s.setRequestedDiscount(amount);s.setDiscountReason(r.getReason());s.setDiscountStatus(r.getStatus());
  if(approved){s.setApprovedDiscount(amount);s.setTotalAmount(s.getOriginalAmount().subtract(amount));}
  event(a,s,approved?"DISCOUNT_APPLIED":"DISCOUNT_REQUESTED",r.getReason(),"requestId="+r.getId()+";amount="+amount+";policy="+r.getPolicySnapshot());
 }
 public void collect(AcademyAuthorization.Actor a,AcademyRunningSession s,BigDecimal total,String sport){
  boolean legacyFractional=zero(s.getTotalAmount()).stripTrailingZeros().scale()>0||zero(s.getPaidAmount()).stripTrailingZeros().scale()>0;
  total=money(total,legacyFractional?"LEGACY":sport);var prior=zero(s.getPaidAmount());if(total.compareTo(prior)<0||total.compareTo(zero(s.getTotalAmount()))>0)throw new ArenaOpsException(ErrorCode.INVALID_REQUEST);
  var delta=total.subtract(prior);if(delta.signum()>0){var p=new AcademyPayment();p.setAcademyId(a.academyId());p.setSessionId(s.getId());p.setAmount(delta);var now=now(a.academyId());p.setPaymentDate(now.toLocalDate());p.setRecordedAt(now);p.setRecordedBy(a.user().getUsername());p.setMethod("OTHER");p.setReference("SESSION_COLLECTION");payments.save(p);}
  s.setPaidAmount(total);
  event(a,s,"PAYMENT_RECORDED",null,"increment="+delta+";cumulative="+total);
 }
 public Inbox inbox(org.springframework.security.oauth2.jwt.Jwt jwt,String status){var a=authorization.actor(jwt);return new Inbox(approver(a),approver(a)?("ALL".equals(status)?requests.findTop100ByAcademyIdOrderByRequestedAtDesc(a.academyId()):requests.findTop100ByAcademyIdAndStatusOrderByRequestedAtAsc(a.academyId(),"PENDING")):requests.findTop100ByAcademyIdAndRequestedByIdOrderByRequestedAtDesc(a.academyId(),a.user().getId()));}
 public SessionDiscountRequest details(org.springframework.security.oauth2.jwt.Jwt jwt,Long id){var a=authorization.actor(jwt);return requests.findById(id).filter(r->Objects.equals(r.getAcademyId(),a.academyId())&&(approver(a)||Objects.equals(r.getRequestedById(),a.user().getId()))).orElseThrow(()->new ArenaOpsException(ErrorCode.RESOURCE_NOT_FOUND));}
 public SessionDiscountRequest decide(org.springframework.security.oauth2.jwt.Jwt jwt,Long id,Decision d){
  var a=authorization.actor(jwt);if(!approver(a))throw new ArenaOpsException(ErrorCode.ACCESS_DENIED);
  var r=requests.locked(a.academyId(),id).orElseThrow(()->new ArenaOpsException(ErrorCode.RESOURCE_NOT_FOUND));
  if(!"PENDING".equals(r.getStatus())||Objects.equals(r.getRequestedById(),a.user().getId()))throw new ArenaOpsException(ErrorCode.CONFLICT);
  var s=sessions.findByIdAndAcademyIdForUpdate(r.getSessionId(),a.academyId()).orElseThrow(()->new ArenaOpsException(ErrorCode.RESOURCE_NOT_FOUND));
  if(!Objects.equals(s.getDiscountRequestId(),r.getId())||!"PENDING".equals(s.getDiscountStatus()))throw new ArenaOpsException(ErrorCode.CONFLICT);
  if(d.approve()&&r.getAmount().compareTo(zero(s.getOriginalAmount()).subtract(zero(s.getPaidAmount())))>0)throw new ArenaOpsException(ErrorCode.CONFLICT);
  r.setStatus(d.approve()?"APPROVED":"REJECTED");r.setDecidedById(a.user().getId());r.setDecidedBy(a.user().getUsername());r.setDecidedAt(now(a.academyId()));r.setDecisionReason(d.reason().strip());
  s.setDiscountStatus(r.getStatus());if(d.approve()){s.setApprovedDiscount(r.getAmount());s.setTotalAmount(s.getOriginalAmount().subtract(r.getAmount()));if(s.getTotalAmount().compareTo(zero(s.getPaidAmount()))<=0)s.setStatus("STOPPED");}
  requests.save(r);sessions.save(s);event(a,s,"DISCOUNT_"+r.getStatus(),d.reason(),"requestId="+id+";requestedBy="+r.getRequestedBy());return r;
 }
 public void request(org.springframework.security.oauth2.jwt.Jwt jwt,Long id,Discount d){
  var a=authorization.actor(jwt);var s=sessions.findByIdAndAcademyIdForUpdate(id,a.academyId()).orElseThrow(()->new ArenaOpsException(ErrorCode.RESOURCE_NOT_FOUND));requireCheckout(a,s);
  if(!List.of("STOPPED","UNCONFIRMED").contains(s.getStatus()))throw new ArenaOpsException(ErrorCode.INVALID_STATE);
  if(s.getOriginalAmount()==null)s.setOriginalAmount(s.getTotalAmount());discount(a,s,d.amount(),d.reason(),s.getPaidAmount());
  if(s.getTotalAmount().compareTo(zero(s.getPaidAmount()))<=0)s.setStatus("STOPPED");sessions.save(s);
 }
 public SessionDiscountRequest cancelRequest(org.springframework.security.oauth2.jwt.Jwt jwt,Long id){
  var a=authorization.actor(jwt);var r=requests.locked(a.academyId(),id).orElseThrow(()->new ArenaOpsException(ErrorCode.RESOURCE_NOT_FOUND));
  if(!approver(a)&&!Objects.equals(r.getRequestedById(),a.user().getId()))throw new ArenaOpsException(ErrorCode.ACCESS_DENIED);
  if(!"PENDING".equals(r.getStatus()))throw new ArenaOpsException(ErrorCode.CONFLICT);var s=sessions.findByIdAndAcademyIdForUpdate(r.getSessionId(),a.academyId()).orElseThrow();
  r.setStatus("CANCELLED");r.setDecidedBy(a.user().getUsername());r.setDecidedById(a.user().getId());r.setDecidedAt(now(a.academyId()));r.setDecisionReason("Request cancelled");s.setDiscountStatus("CANCELLED");requests.save(r);sessions.save(s);event(a,s,"DISCOUNT_CANCELLED",null,"requestId="+id);return r;
 }
 private void requireCheckout(AcademyAuthorization.Actor a,AcademyRunningSession s){if(!a.manager()&&!a.roles().contains("STAFF")&&(a.membership()==null||!(a.roles().contains("INSTRUCTOR")||"COACH".equals(a.membership().getRole()))||!a.membership().getSportIds().contains(s.getSportId())))throw new ArenaOpsException(ErrorCode.ACCESS_DENIED);}
 public List<SessionCheckoutContracts.Collection> collections(org.springframework.security.oauth2.jwt.Jwt jwt,LocalDate from,LocalDate to){
  var a=authorization.actor(jwt);if(!financial(a))throw new ArenaOpsException(ErrorCode.ACCESS_DENIED);if(from==null||to==null||from.isAfter(to)||java.time.temporal.ChronoUnit.DAYS.between(from,to)>366)throw new ArenaOpsException(ErrorCode.INVALID_REQUEST);
  return payments.sessionPayments(a.academyId(),from,to).stream().map(p->{var s=sessions.findByIdAndAcademyId(p.getSessionId(),a.academyId()).orElseThrow();var sport=sports.findById(s.getSportId()).orElseThrow();return new SessionCheckoutContracts.Collection(s.getId(),s.getSportId(),sport.getName(),p.getAmount(),p.getPaymentDate(),p.getRecordedAt(),p.getRecordedBy(),p.getReference());}).toList();
 }
 public BigDecimal collectedToday(Long academy,LocalDate day){return payments.sessionPayments(academy,day,day).stream().map(AcademyPayment::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);}
 public List<AcademyPayment> todayPayments(Long academy,LocalDate day){return payments.sessionPayments(academy,day,day);}
 public void event(AcademyAuthorization.Actor a,AcademyRunningSession s,String type,String reason,String metadata){audit.record("SESSION_"+type,a.academyId(),AuditActorType.CUSTOMER,a.user().getUsername(),null,s.getStatus(),reason,"sessionId="+s.getId()+";"+metadata);}
}
