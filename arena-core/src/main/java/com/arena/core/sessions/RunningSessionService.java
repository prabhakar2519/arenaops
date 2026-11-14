package com.arena.core.sessions;

import static com.arena.core.sessions.RunningSessionContracts.*;

import com.arena.core.entity.CustomerEntity;
import com.arena.core.exception.ArenaOpsException;
import com.arena.core.exception.ErrorCode;
import com.arena.core.facility.FacilityService;
import com.arena.core.repository.CustomerRepository;
import com.arena.core.training.AcademyAuthorization;
import com.arena.core.training.AcademyPlayArea;
import com.arena.core.training.AcademyPlayAreaRepository;
import com.arena.core.training.AcademySport;
import com.arena.core.training.AcademySportRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional
public class RunningSessionService {
  @org.springframework.beans.factory.annotation.Autowired(required=false) private com.arena.core.publicclub.ClubCustomerService clubCustomers;
  public void validatePublicWindow(Long academy,Long facility,LocalDateTime from,LocalDateTime to){assertWindowAvailable(academy,facility,from,to,null);}
  private static final List<String> HISTORY_STATUSES = List.of("STOPPED", "UNCONFIRMED", "CANCELLED");
  private final AcademyAuthorization authorization;
  private final AcademyPlayAreaRepository facilities;
  private final FacilityService facilityService;
  private final AcademySportRepository sports;
  private final AcademyRunningSessionRepository sessions;
  private final CustomerRepository customers;
  private final SessionCheckoutService checkout;

  public RunningSessionService(AcademyAuthorization authorization, AcademyPlayAreaRepository facilities,
      AcademySportRepository sports, AcademyRunningSessionRepository sessions, CustomerRepository customers,
      FacilityService facilityService,SessionCheckoutService checkout) {
    this.authorization = authorization; this.facilities = facilities; this.sports = sports;
    this.sessions = sessions; this.customers = customers;
    this.facilityService = facilityService;this.checkout=checkout;
  }

  public SessionView start(Jwt jwt, Start request) {
    var actor = authorization.actor(jwt);
    var facility = facilityService.requireEligibleLocked(jwt, actor.academyId(), request.facilityId());
    var sport = sport(actor.academyId(), facility.getSportId()); requireOperator(actor, sport.getId());
    if (!supported(sport.getCatalogueCode())) throw new ArenaOpsException(ErrorCode.INVALID_STATE);
    String customerMobile=null;if(request.mobile()!=null&&!request.mobile().isBlank()){com.arena.core.publicclub.ClubCustomerService.name(request.playerName());customerMobile=com.arena.core.publicclub.ClubCustomerService.checkinMobile(request.mobile());if(clubCustomers==null)throw new ArenaOpsException(ErrorCode.INVALID_STATE);}
    var now = now(actor.academyId());
    if (request.plannedEndAt()!=null && !request.plannedEndAt().isAfter(now)) throw new ArenaOpsException(ErrorCode.INVALID_REQUEST);
    // An actual running session always occupies its facility, even beyond its planned end.
    if (sessions.existsByAcademyIdAndFacilityIdAndStatus(actor.academyId(),facility.getId(),"STARTED")) throw new ArenaOpsException(ErrorCode.CONFLICT);
    assertWindowAvailable(actor.academyId(),facility.getId(),now,request.plannedEndAt(),null);
    var session = new AcademyRunningSession();
    session.setAcademyId(actor.academyId());session.setSportId(sport.getId());session.setFacilityId(facility.getId());
    session.setStatus("STARTED");session.setStartedAt(now);session.setPlannedEndAt(request.plannedEndAt());
    session.setRatePerHour(facility.getHourlyRate());session.setPlayerName(clean(request.playerName()));session.setStartedBy(actor.user().getUsername());
    session=sessions.saveAndFlush(session);
    if(customerMobile!=null)clubCustomers.visit(actor.academyId(),request.playerName(),customerMobile,session.getId(),session.getStartedAt().atZone(zone(actor.academyId())).toInstant());
    return view(session,facility,sport);
  }

  public SessionView reserve(Jwt jwt, Reserve request) {
    var actor = authorization.actor(jwt);
    var facility = facilityService.requireEligibleLocked(jwt,actor.academyId(),request.facilityId());
    var sport = sport(actor.academyId(),facility.getSportId());requireOperator(actor,sport.getId());
    if (!supported(sport.getCatalogueCode())) throw new ArenaOpsException(ErrorCode.INVALID_STATE);
    var now=now(actor.academyId());
    if(request.reservedStart()==null || request.reservedEnd()==null || request.reservedStart().isBefore(now) || !request.reservedEnd().isAfter(request.reservedStart())) throw new ArenaOpsException(ErrorCode.INVALID_REQUEST);
    assertWindowAvailable(actor.academyId(),facility.getId(),request.reservedStart(),request.reservedEnd(),null);
    var session=new AcademyRunningSession();session.setAcademyId(actor.academyId());session.setSportId(sport.getId());session.setFacilityId(facility.getId());
    session.setStatus("RESERVED");session.setStartedAt(now);session.setReservedStart(request.reservedStart());session.setReservedEnd(request.reservedEnd());
    session.setRatePerHour(facility.getHourlyRate());session.setPlayerName(clean(request.playerName()));session.setStartedBy(actor.user().getUsername());
    return view(sessions.saveAndFlush(session),facility,sport);
  }

  private void assertWindowAvailable(Long academy,Long facility,LocalDateTime from,LocalDateTime to,Long excluded) {
    // The tenant and facility write locks serialize availability checks with inserts/starts.
    for(var other:sessions.findByAcademyIdAndFacilityIdAndStatusIn(academy,facility,List.of("RESERVED","STARTED"))) {
      if(excluded!=null && java.util.Objects.equals(other.getId(),excluded))continue;
      var start="RESERVED".equals(other.getStatus())?other.getReservedStart():other.getStartedAt();
      var end="RESERVED".equals(other.getStatus())?other.getReservedEnd():other.getPlannedEndAt();
      if(start==null || ((to==null || start.isBefore(to)) && (end==null || end.isAfter(from)))) throw new ArenaOpsException(ErrorCode.CONFLICT);
    }
  }

  public SessionView startReserved(Jwt jwt, Long id) {
    var actor = authorization.actor(jwt);
    var session = lockedSession(id, actor.academyId());
    requireOperator(actor, session.getSportId());
    if (!"RESERVED".equals(session.getStatus())) throw new ArenaOpsException(ErrorCode.INVALID_STATE);
    var facility = facilityService.requireEligibleLocked(jwt, actor.academyId(), session.getFacilityId());
    if (sessions.existsByAcademyIdAndFacilityIdAndStatus(actor.academyId(), facility.getId(), "STARTED")) throw new ArenaOpsException(ErrorCode.CONFLICT);
    var now=now(actor.academyId());
    if(session.getReservedStart()==null || now.isBefore(session.getReservedStart()) || !now.isBefore(session.getReservedEnd())) throw new ArenaOpsException(ErrorCode.INVALID_STATE);
    assertWindowAvailable(actor.academyId(),facility.getId(),now,session.getReservedEnd(),session.getId());
    session.setPlannedEndAt(session.getReservedEnd());
    session.setStatus("STARTED"); session.setStartedAt(now);
    session.setStartedBy(actor.user().getUsername()); session.setRatePerHour(facility.getHourlyRate());
    if(clubCustomers!=null)clubCustomers.reservedStarted(actor,session);
    return view(sessions.saveAndFlush(session), facility, sport(actor.academyId(), session.getSportId()));
  }

  public SessionView cancelReservation(Jwt jwt, Long id) {
    var actor = authorization.actor(jwt);
    var session = lockedSession(id, actor.academyId());
    requireOperator(actor, session.getSportId());
    if (!"RESERVED".equals(session.getStatus())) throw new ArenaOpsException(ErrorCode.INVALID_STATE);
    session.setStatus("CANCELLED"); session.setStopType("RESERVATION_CANCEL");
    session.setEndedAt(session.getStartedAt()); session.setStoppedAt(now(actor.academyId()));
    session.setStoppedBy(actor.user().getUsername()); session.setTotalAmount(BigDecimal.ZERO);
    return view(sessions.saveAndFlush(session), facility(actor.academyId(), session.getFacilityId()), sport(actor.academyId(), session.getSportId()));
  }

  public SessionView stop(Jwt jwt, Long id, Stop request) {
    var actor = authorization.actor(jwt);
    AcademyRunningSession session = lockedSession(id, actor.academyId());
    requireOperator(actor, session.getSportId());
    if (!"STARTED".equals(session.getStatus())) throw new ArenaOpsException(ErrorCode.INVALID_STATE);
    LocalDateTime now=now(actor.academyId());
    if(request.manualDurationMinutes()!=null)throw new ArenaOpsException(ErrorCode.INVALID_REQUEST);
    var quote=checkout.consume(actor,session,request.quoteToken(),now);
    var endedAt=quote.getCapturedAt();applyOpenPause(session,endedAt);
    session.setStoppedAt(now);session.setEndedAt(endedAt);session.setStoppedBy(actor.user().getUsername());
    var code=sportCode(session);var paid=checkout.money(request.paidAmount(),code);
    if((request.quickStop()||request.markUnsettled())&&paid.signum()>0)throw new ArenaOpsException(ErrorCode.INVALID_REQUEST);
    if(!request.quickStop()&&quote.getBillableSeconds()<60){
      if(paid.signum()>0||zero(request.discountAmount()).signum()>0)throw new ArenaOpsException(ErrorCode.INVALID_REQUEST);
      session.setStatus("CANCELLED");session.setStopType("IGNORED_SHORT");session.setOriginalAmount(BigDecimal.ZERO);session.setTotalAmount(BigDecimal.ZERO);session.setPaidAmount(BigDecimal.ZERO);
    }else{
      session.setOriginalAmount(quote.getOriginalAmount());session.setTotalAmount(quote.getOriginalAmount());
      checkout.discount(actor,session,request.discountAmount(),request.discountReason(),request.quickStop()||request.markUnsettled()?BigDecimal.ZERO:paid);
      checkout.collect(actor,session,request.quickStop()||request.markUnsettled()?BigDecimal.ZERO:paid,code);
      boolean unsettled=request.quickStop()||request.markUnsettled();
      session.setStatus(unsettled?"UNCONFIRMED":"STOPPED");session.setStopType(request.quickStop()?"QUICK":request.markUnsettled()?"UNSETTLED":"AUTO");
    }
    checkout.event(actor,session,"STOPPED",null,"quotedAt="+endedAt+";confirmedAt="+now+";gross="+session.getOriginalAmount()+";net="+session.getTotalAmount());
    return view(sessions.save(session), facility(actor.academyId(), session.getFacilityId()), sport(actor.academyId(), session.getSportId()));
  }

  public SessionCheckoutContracts.Quote stopQuote(Jwt jwt,Long id){
    var a=authorization.actor(jwt);var session=lockedSession(id,a.academyId());requireOperator(a,session.getSportId());
    if(!"STARTED".equals(session.getStatus()))throw new ArenaOpsException(ErrorCode.INVALID_STATE);
    var captured=now(a.academyId()).truncatedTo(java.time.temporal.ChronoUnit.MICROS);long seconds=billableSeconds(session,captured);
    long paused=Math.max(session.getTotalPausedSeconds()==null?0:session.getTotalPausedSeconds(),0)+(session.getPausedAt()==null?0:Math.max(Duration.between(session.getPausedAt(),captured).getSeconds(),0));
    return checkout.issue(a,session,captured,seconds,paused,amount(session.getRatePerHour(),seconds,sportCode(session)));
  }
  public void cancelQuote(Jwt jwt,Long id,String token){var a=authorization.actor(jwt);var session=lockedSession(id,a.academyId());requireOperator(a,session.getSportId());checkout.cancel(a,session,token);}

  public SessionView pause(Jwt jwt, Long id) {
    var actor = authorization.actor(jwt);
    AcademyRunningSession session = lockedSession(id, actor.academyId());
    requireOperator(actor, session.getSportId());
    if (!"STARTED".equals(session.getStatus()) || session.getPausedAt() != null) throw new ArenaOpsException(ErrorCode.INVALID_STATE);
    session.setPausedAt(now(actor.academyId()));
    return view(sessions.save(session), facility(actor.academyId(), session.getFacilityId()), sport(actor.academyId(), session.getSportId()));
  }

  public SessionView resume(Jwt jwt, Long id) {
    var actor = authorization.actor(jwt);
    AcademyRunningSession session = lockedSession(id, actor.academyId());
    requireOperator(actor, session.getSportId());
    if (!"STARTED".equals(session.getStatus()) || session.getPausedAt() == null) throw new ArenaOpsException(ErrorCode.INVALID_STATE);
    applyOpenPause(session, now(actor.academyId()));
    return view(sessions.save(session), facility(actor.academyId(), session.getFacilityId()), sport(actor.academyId(), session.getSportId()));
  }

  public SessionView settle(Jwt jwt, Long id, BigDecimal paidAmount) {
    var actor = authorization.actor(jwt);
    AcademyRunningSession session = lockedSession(id, actor.academyId());
    requireOperator(actor, session.getSportId());
    if (!"UNCONFIRMED".equals(session.getStatus()) && !("STOPPED".equals(session.getStatus()) && zero(session.getTotalAmount()).compareTo(zero(session.getPaidAmount())) > 0)) throw new ArenaOpsException(ErrorCode.INVALID_STATE);
    BigDecimal amount=paidAmount==null?session.getTotalAmount():paidAmount;
    checkout.collect(actor,session,amount,sportCode(session));
    session.setStatus(session.getPaidAmount().compareTo(zero(session.getTotalAmount()))<0?"UNCONFIRMED":"STOPPED");session.setStopType("SETTLED");session.setStoppedBy(actor.user().getUsername());
    return view(sessions.save(session), facility(actor.academyId(), session.getFacilityId()), sport(actor.academyId(), session.getSportId()));
  }

  private void expireReservations(Long academyId) {
    var now=now(academyId);
    for(var held:sessions.findByAcademyIdAndStatusOrderByStartedAtAsc(academyId,"RESERVED")) {
      if(held.getReservedEnd()!=null && !held.getReservedEnd().isAfter(now)) {
        held.setStatus("CANCELLED");held.setStopType("RESERVATION_EXPIRED");held.setEndedAt(held.getStartedAt());held.setStoppedAt(now);held.setTotalAmount(BigDecimal.ZERO);sessions.save(held);
      }
    }
  }

  public Dashboard dashboard(Jwt jwt) {
    var actor = authorization.actor(jwt);
    Long academyId = actor.academyId();
    expireReservations(academyId);
    List<SessionView> active = sessions.findByAcademyIdAndStatusOrderByStartedAtAsc(academyId, "STARTED").stream()
      .filter(session -> canOperateSport(actor, session.getSportId())).map(this::view).toList();
    List<SessionView> reserved = sessions.findByAcademyIdAndStatusOrderByStartedAtAsc(academyId, "RESERVED").stream()
      .filter(session -> session.getReservedEnd()==null || session.getReservedEnd().isAfter(now(academyId)))
      .filter(session -> canOperateSport(actor, session.getSportId())).map(this::view).toList();
    List<SessionView> unsettled = sessions.findUnsettled(academyId).stream()
      .filter(session -> canOperateSport(actor, session.getSportId())).map(this::view).toList();
    List<SessionView> recent = sessions.findTop100ByAcademyIdAndStatusInOrderByStartedAtDescIdDesc(academyId, HISTORY_STATUSES).stream()
      .filter(session -> canOperateSport(actor, session.getSportId())).map(this::view).toList();
    List<AcademyPlayArea> academyFacilities = facilities.findByAcademyIdOrderByName(academyId).stream()
      .filter(facility -> canOperateSport(actor, facility.getSportId())).toList();
    List<FacilityOption> configured = academyFacilities.stream().map(facility -> {
      AcademySport sport = sport(academyId, facility.getSportId());
      boolean eligible = canOperateSport(actor, sport.getId()) && facility.isActive() && sport.isActive() && sport.isSessions() && facility.getFacilityType() != null
        && facility.getHourlyRate() != null && facility.getHourlyRate().signum() > 0 && supported(sport.getCatalogueCode());
      boolean inUse = java.util.stream.Stream.concat(active.stream(), reserved.stream().filter(session -> session.reservedStart()==null || !session.reservedStart().isAfter(now(academyId)))).anyMatch(session -> session.facilityId().equals(facility.getId()));
      return new FacilityOption(facility.getId(), sport.getId(), sport.getCatalogueCode(), sport.getName(), facility.getCode(),
        facility.getDisplayName() == null ? facility.getCode() : facility.getDisplayName(), facility.getFacilityType(),
        facility.isActive(), facility.getHourlyRate(), facility.getFrameRate(), facility.getFacilityType() != null
          && facility.getHourlyRate() != null, eligible && !inUse);
    }).toList();
    List<AcademySport> academySports = sports.findByAcademyIdOrderByName(academyId).stream()
      .filter(AcademySport::isActive).filter(sport -> canOperateSport(actor, sport.getId())).toList();
    Map<Long, Long> activeFacilityCounts = academyFacilities.stream().filter(AcademyPlayArea::isActive)
      .collect(Collectors.groupingBy(AcademyPlayArea::getSportId, Collectors.counting()));
    Set<Long> occupiedFacilityIds = active.stream().map(SessionView::facilityId).collect(Collectors.toSet());
    Map<Long, Long> occupiedFacilityCounts = academyFacilities.stream()
      .filter(AcademyPlayArea::isActive).filter(facility -> occupiedFacilityIds.contains(facility.getId()))
      .collect(Collectors.groupingBy(AcademyPlayArea::getSportId, Collectors.counting()));
    Map<Long, Long> availableFacilityCounts = configured.stream().filter(FacilityOption::available)
      .collect(Collectors.groupingBy(FacilityOption::sportId, Collectors.counting()));
    List<FacilitySummary> facilitySummary = academySports.stream().map(sport -> new FacilitySummary(
      sport.getId(), sport.getCatalogueCode(), sport.getName(), activeFacilityCounts.getOrDefault(sport.getId(), 0L),
      occupiedFacilityCounts.getOrDefault(sport.getId(), 0L), availableFacilityCounts.getOrDefault(sport.getId(), 0L))).toList();
    LocalDateTime start = LocalDate.now(zone(academyId)).atStartOfDay();
    LocalDateTime end = start.plusDays(1);
    Map<Long, AcademyRunningSessionRepository.DailyCollectionTotals> collections = sessions.dailyCollections(academyId, start, end).stream()
      .filter(value -> canOperateSport(actor, value.getSportId()))
      .collect(Collectors.toMap(AcademyRunningSessionRepository.DailyCollectionTotals::getSportId, Function.identity()));
    List<DailyCollection> collectionsBySport = new ArrayList<>();
    for (AcademySport sport : academySports) {
      var totals = collections.remove(sport.getId());
      BigDecimal charged = totals == null ? BigDecimal.ZERO : zero(totals.getCharged());
      BigDecimal collected = totals == null ? BigDecimal.ZERO : zero(totals.getCollected());
      collectionsBySport.add(new DailyCollection(sport.getId(), sport.getCatalogueCode(), sport.getName(),
          charged, collected, charged.subtract(collected).max(BigDecimal.ZERO)));
    }
    collections.values().forEach(totals -> {
      BigDecimal charged = zero(totals.getCharged());
      BigDecimal collected = zero(totals.getCollected());
      collectionsBySport.add(new DailyCollection(totals.getSportId(), totals.getSportCode(), totals.getSportName(),
          charged, collected, charged.subtract(collected).max(BigDecimal.ZERO)));
    });
    BigDecimal charges = collectionsBySport.stream().map(DailyCollection::charged).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal collected = collectionsBySport.stream().map(DailyCollection::collected).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal current = active.stream().map(SessionView::currentAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    // Accrual charges and current debt differ from actual dated cash collections.
    var actual=checkout.todayPayments(academyId,start.toLocalDate());
    var cashBySport=new java.util.HashMap<Long,BigDecimal>();
    for(var payment:actual){sessions.findByIdAndAcademyId(payment.getSessionId(),academyId).ifPresent(value->cashBySport.merge(value.getSportId(),payment.getAmount(),BigDecimal::add));}
    var debtBySport=new java.util.HashMap<Long,BigDecimal>();for(var value:unsettled)debtBySport.merge(value.sportId(),value.outstandingAmount(),BigDecimal::add);
    var datedCollections=collectionsBySport.stream().map(v->new DailyCollection(v.sportId(),v.sport(),v.sportName(),v.charged(),cashBySport.getOrDefault(v.sportId(),BigDecimal.ZERO),debtBySport.getOrDefault(v.sportId(),BigDecimal.ZERO))).toList();
    collected=actual.stream().map(com.arena.core.billing.AcademyPayment::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
    var debt=unsettled.stream().map(SessionView::outstandingAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
    if(!checkout.financial(actor))return new Dashboard(active,recent.stream().map(v->redact(v,actor)).toList(),configured,null,null,null,null,facilitySummary,List.of(),reserved,unsettled);
    return new Dashboard(active,recent,configured,current,charges,collected,debt,facilitySummary,datedCollections,reserved,unsettled);
  }

  public org.springframework.data.domain.Page<SessionView> history(Jwt jwt,LocalDate from,LocalDate to,String status,String search,int page,int size) {
    var actor=authorization.actor(jwt);var today=LocalDate.now(zone(actor.academyId()));
    LocalDate begin=from==null?today.minusDays(29):from, finish=to==null?today:to;
    if(finish.isBefore(begin)||page<0||size<1||size>100|| (status!=null&&!status.isBlank()&&!HISTORY_STATUSES.contains(status)))throw new ArenaOpsException(ErrorCode.INVALID_REQUEST);
    var allowed=sports.findByAcademyIdOrderByName(actor.academyId()).stream().filter(s->canOperateSport(actor,s.getId())).map(AcademySport::getId).toList();
    org.springframework.data.jpa.domain.Specification<AcademyRunningSession> spec=(r,q,cb)->{
      var predicates=new ArrayList<jakarta.persistence.criteria.Predicate>();
      predicates.add(cb.equal(r.get("academyId"),actor.academyId()));predicates.add(r.get("sportId").in(allowed));
      predicates.add(r.get("status").in(status==null||status.isBlank()?HISTORY_STATUSES:List.of(status)));
      predicates.add(cb.greaterThanOrEqualTo(r.get("startedAt"),begin.atStartOfDay()));predicates.add(cb.lessThan(r.get("startedAt"),finish.plusDays(1).atStartOfDay()));
      if(search!=null&&!search.isBlank()) {
        String term="%"+search.strip().toLowerCase(Locale.ROOT).replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";
        predicates.add(cb.like(cb.lower(r.get("playerName")),term,'\\'));
      }
      return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
    };
    return sessions.findAll(spec,org.springframework.data.domain.PageRequest.of(page,size,org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC,"startedAt","id"))).map(value->historyView(value,actor));
  }

  @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
  public byte[] historyWorkbook(Jwt jwt,LocalDate from,LocalDate to,String status,String search){
    var actor=authorization.actor(jwt);var rows=new ArrayList<SessionView>();
    org.springframework.data.domain.Page<SessionView> batch;int page=0;
    do{batch=history(jwt,from,to,status,search,page++,100);rows.addAll(batch.getContent());}while(batch.hasNext());
    return SessionHistoryWorkbook.write(rows,checkout.financial(actor));
  }
  private SessionView historyView(AcademyRunningSession s,AcademyAuthorization.Actor a){
    SessionView v=view(s);if(checkout.financial(a))return v;
    // History-only exception: original charge and coarse settlement state. Never operator-based financial access.
    return new SessionView(v.id(),v.facilityId(),v.facilityCode(),v.facilityName(),v.sportId(),v.sport(),v.facilityType(),v.status(),v.startedAt(),v.endedAt(),v.stoppedAt(),v.pausedAt(),v.durationSeconds(),v.totalPausedSeconds(),null,null,null,null,null,v.paymentStatus(),v.playerName(),v.startedBy(),v.stoppedBy(),v.stopType(),v.reservedStart(),v.reservedEnd(),v.plannedEndAt(),v.originalAmount(),null,null,null,null,null,null,false,v.catalogueCode(),v.playerMobile());
  }
  private SessionView redact(SessionView v,AcademyAuthorization.Actor a){
    if(checkout.financial(a)||java.util.Objects.equals(a.user().getUsername(),v.startedBy())||java.util.Objects.equals(a.user().getUsername(),v.stoppedBy()))return v;
    return new SessionView(v.id(),v.facilityId(),v.facilityCode(),v.facilityName(),v.sportId(),v.sport(),v.facilityType(),v.status(),v.startedAt(),v.endedAt(),v.stoppedAt(),v.pausedAt(),v.durationSeconds(),v.totalPausedSeconds(),null,null,null,null,null,null,v.playerName(),v.startedBy(),v.stoppedBy(),v.stopType(),v.reservedStart(),v.reservedEnd(),v.plannedEndAt(),null,null,null,null,null,null,null,false,v.catalogueCode(),v.playerMobile());
  }

  private SessionView view(AcademyRunningSession session) {
    return view(session, facility(session.getAcademyId(), session.getFacilityId()), sport(session.getAcademyId(), session.getSportId()));
  }

  private SessionView view(AcademyRunningSession session, AcademyPlayArea facility, AcademySport sport) {
    LocalDateTime end = session.getEndedAt() == null ? now(session.getAcademyId()) : session.getEndedAt();
    long seconds = "RESERVED".equals(session.getStatus()) ? 0 : billableSeconds(session, end);
    BigDecimal current = "STARTED".equals(session.getStatus()) ? amount(session.getRatePerHour(), seconds, sport.getCatalogueCode()) : zero(session.getTotalAmount());
    BigDecimal total = session.getTotalAmount(); BigDecimal paid = zero(session.getPaidAmount());
    BigDecimal outstanding = total == null ? BigDecimal.ZERO : total.subtract(paid).max(BigDecimal.ZERO);
    String paymentStatus = "RESERVED".equals(session.getStatus()) ? "NOT_DUE" : total == null ? "RUNNING" : outstanding.signum() == 0 ? "PAID" : paid.signum() > 0 ? "PARTIALLY_PAID" : "UNPAID";
    return new SessionView(session.getId(), facility.getId(), facility.getCode(),
        facility.getDisplayName() == null ? facility.getCode() : facility.getDisplayName(), sport.getId(), sport.getName(),
        facility.getFacilityType(), session.getStatus(), session.getStartedAt(), session.getEndedAt(), session.getStoppedAt(), session.getPausedAt(),
        seconds, session.getTotalPausedSeconds(), session.getRatePerHour(), current, total, paid, outstanding,
        paymentStatus, session.getPlayerName(), session.getStartedBy(), session.getStoppedBy(), session.getStopType(),session.getReservedStart(),session.getReservedEnd(),session.getPlannedEndAt(),session.getOriginalAmount()==null?total:session.getOriginalAmount(),zero(session.getApprovedDiscount()),zero(session.getRequestedDiscount()),session.getDiscountStatus(),session.getDiscountReason(),session.getDiscountRequestId(),total==null?"RUNNING":outstanding.signum()==0?"SETTLED":"UNSETTLED",true,sport.getCatalogueCode(),clubCustomers==null||!"STARTED".equals(session.getStatus())?null:clubCustomers.sessionMobile(session.getAcademyId(),session.getId()));
  }

  private AcademyRunningSession lockedSession(Long id, Long academyId) {
    return sessions.findByIdAndAcademyIdForUpdate(id, academyId).orElseThrow(() -> new ArenaOpsException(ErrorCode.RESOURCE_NOT_FOUND));
  }
  private AcademyPlayArea facility(Long academyId, Long id) { return facilities.findById(id).filter(value -> academyId.equals(value.getAcademyId())).orElseThrow(() -> new ArenaOpsException(ErrorCode.RESOURCE_NOT_FOUND)); }
  private AcademySport sport(Long academyId, Long id) { return sports.findById(id).filter(value -> academyId.equals(value.getAcademyId())).orElseThrow(() -> new ArenaOpsException(ErrorCode.RESOURCE_NOT_FOUND)); }
  private void requireOperator(AcademyAuthorization.Actor actor, Long sportId) {
    if (!canOperateSport(actor, sportId)) throw new ArenaOpsException(ErrorCode.ACCESS_DENIED);
  }
  private boolean canOperateSport(AcademyAuthorization.Actor actor, Long sportId) {
    if (actor.manager() || actor.roles().contains("STAFF")) return true;
    if (actor.membership() == null) return false;
    boolean coaching = actor.roles().contains("INSTRUCTOR") || "COACH".equals(actor.membership().getRole());
    return coaching && actor.membership().getSportIds().contains(sportId);
  }
  private LocalDateTime now(Long academyId) { return LocalDateTime.now(zone(academyId)); }
  private ZoneId zone(Long academyId) {
    return customers.findById(academyId).map(CustomerEntity::getTrainingTimezone).filter(value -> value != null && !value.isBlank())
        .map(value -> { try { return ZoneId.of(value); } catch (Exception ignored) { return ZoneId.of("Asia/Kolkata"); } }).orElse(ZoneId.of("Asia/Kolkata"));
  }
  private boolean supported(String code) { return "SNOOKER".equalsIgnoreCase(code) || "BADMINTON".equalsIgnoreCase(code); }
  private String sportCode(AcademyRunningSession session) { return sport(session.getAcademyId(), session.getSportId()).getCatalogueCode().toUpperCase(Locale.ROOT); }
  private long billableSeconds(AcademyRunningSession session, LocalDateTime end) {
    long elapsed = Math.max(Duration.between(session.getStartedAt(), end).getSeconds(), 0);
    long paused = Math.max(session.getTotalPausedSeconds() == null ? 0 : session.getTotalPausedSeconds(), 0);
    if (session.getPausedAt() != null) paused += Math.max(Duration.between(session.getPausedAt(), end).getSeconds(), 0);
    return Math.max(elapsed - paused, 0);
  }
  private BigDecimal amount(BigDecimal rate, long seconds, String sport) {
    BigDecimal raw = rate.multiply(BigDecimal.valueOf(seconds)).divide(BigDecimal.valueOf(3600), 8, RoundingMode.HALF_UP).setScale(2, RoundingMode.HALF_UP);
    return "SNOOKER".equalsIgnoreCase(sport) ? raw.setScale(0, RoundingMode.DOWN) : raw;
  }
  private void applyOpenPause(AcademyRunningSession session, LocalDateTime resumedAt) {
    if (session.getPausedAt() == null) return;
    long paused = Math.max(Duration.between(session.getPausedAt(), resumedAt).getSeconds(), 0);
    session.setTotalPausedSeconds(Math.max(session.getTotalPausedSeconds() == null ? 0 : session.getTotalPausedSeconds(), 0) + paused);
    session.setPausedAt(null);
  }
  private BigDecimal zero(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
  private String clean(String value) { return value == null || value.isBlank() ? null : value.strip(); }
}