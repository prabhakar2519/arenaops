package com.arena.core.sessions;

import static com.arena.core.sessions.RunningSessionContracts.Stop;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.arena.core.entity.AppUserEntity;
import com.arena.core.facility.FacilityService;
import com.arena.core.repository.CustomerRepository;
import com.arena.core.training.AcademyAuthorization;
import com.arena.core.training.AcademyPlayArea;
import com.arena.core.training.AcademyPlayAreaRepository;
import com.arena.core.training.AcademySport;
import com.arena.core.training.AcademySportRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.transaction.annotation.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class RunningSessionServiceTest {
  @Mock private AcademyAuthorization authorization;
  @Mock private AcademyPlayAreaRepository facilities;
    @Mock private FacilityService facilityService;
  @Mock private AcademySportRepository sports;
  @Mock private AcademyRunningSessionRepository sessions;
  @Mock private CustomerRepository customers;
  @Mock private SessionCheckoutService checkout;
  private RunningSessionService service;
  private Jwt jwt;
  private AcademyPlayArea facility;
  private AcademySport sport;

  @BeforeEach
  void setUp() {
    jwt = Jwt.withTokenValue("token").header("alg", "none").subject("owner").build();
    facility = new AcademyPlayArea(); facility.setId(5L); facility.setAcademyId(12L); facility.setSportId(3L);
    facility.setCode("E1"); facility.setFacilityType("ENGLISH"); facility.setActive(true); facility.setHourlyRate(new BigDecimal("100.00"));
    sport = new AcademySport(); sport.setId(3L); sport.setAcademyId(12L); sport.setName("Snooker"); sport.setCatalogueCode("SNOOKER"); sport.setActive(true); sport.setSessions(true);
    service = new RunningSessionService(authorization, facilities, sports, sessions, customers, facilityService,checkout);
    org.mockito.Mockito.lenient().when(checkout.financial(any())).thenReturn(true);
    org.mockito.Mockito.lenient().when(checkout.money(any(),any())).thenAnswer(i->i.getArgument(0)==null?BigDecimal.ZERO:i.getArgument(0));
    org.mockito.Mockito.lenient().when(checkout.consume(any(),any(),any(),any())).thenAnswer(i->{
      AcademyRunningSession s=i.getArgument(1);LocalDateTime now=i.getArgument(3);var q=new SessionStopQuote();q.setCapturedAt(now);
      long seconds=org.springframework.test.util.ReflectionTestUtils.invokeMethod(service,"billableSeconds",s,now);q.setBillableSeconds(seconds);
      BigDecimal amount=org.springframework.test.util.ReflectionTestUtils.invokeMethod(service,"amount",s.getRatePerHour(),seconds,sport.getCatalogueCode());q.setOriginalAmount(amount);return q;});
    org.mockito.Mockito.lenient().doAnswer(i->{AcademyRunningSession s=i.getArgument(1);BigDecimal paid=i.getArgument(2);if(paid.compareTo(s.getPaidAmount())<0||paid.compareTo(s.getTotalAmount())>0)throw new com.arena.core.exception.ArenaOpsException(com.arena.core.exception.ErrorCode.INVALID_REQUEST);s.setPaidAmount(paid);return null;}).when(checkout).collect(any(),any(),any(),any());

  }

  @Test
  void pauseTimeIsExcludedAndSnookerRoundsDown() {
    stubOwner();
    AcademyRunningSession session = running(25, 10);
    when(facilities.findById(5L)).thenReturn(Optional.of(facility));
    when(sports.findById(3L)).thenReturn(Optional.of(sport));
    when(sessions.findByIdAndAcademyIdForUpdate(8L, 12L)).thenReturn(Optional.of(session));
    when(sessions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    var result = service.stop(jwt, 8L, new Stop(BigDecimal.ZERO, null, null, false, false));

    assertEquals("STOPPED", result.status());
    assertEquals(15L * 60, result.durationSeconds());
    assertEquals(new BigDecimal("25"), result.totalAmount());
  }

  @Test
  void badmintonRetainsTwoDecimalAmountAfterPausedTimeIsRemoved() {
    stubOwner();
    sport.setName("Badminton"); sport.setCatalogueCode("BADMINTON"); facility.setFacilityType("COURT");
    facility.setCode("C1");
    AcademyRunningSession session = running(20, 0);
    when(facilities.findById(5L)).thenReturn(Optional.of(facility));
    when(sports.findById(3L)).thenReturn(Optional.of(sport));
    when(sessions.findByIdAndAcademyIdForUpdate(8L, 12L)).thenReturn(Optional.of(session));
    when(sessions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    var result = service.stop(jwt, 8L, new Stop(BigDecimal.ZERO, null, null, false, false));

    assertEquals(new BigDecimal("33.33"), result.totalAmount());
  }

  @Test
  void subMinuteStopIsCancelledWithoutCharge() {
    stubOwner();
    AcademyRunningSession session = running(0, 0);
    session.setStartedAt(LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")).minusSeconds(30));
    when(facilities.findById(5L)).thenReturn(Optional.of(facility));
    when(sports.findById(3L)).thenReturn(Optional.of(sport));
    when(sessions.findByIdAndAcademyIdForUpdate(8L, 12L)).thenReturn(Optional.of(session));
    when(sessions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    var result = service.stop(jwt, 8L, new Stop(BigDecimal.ZERO, null, null, false, false));

    assertEquals("CANCELLED", result.status());
    assertEquals(BigDecimal.ZERO, result.totalAmount());
  }

  @Test
  void dashboardReturnsEmptyAcademySummariesWithoutFacilitiesOrSessions() {
    stubOwner();
    when(facilities.findByAcademyIdOrderByName(12L)).thenReturn(List.of());
    when(sports.findByAcademyIdOrderByName(12L)).thenReturn(List.of());
    when(sessions.findByAcademyIdAndStatusOrderByStartedAtAsc(12L, "RESERVED")).thenReturn(List.of());
    when(sessions.findByAcademyIdAndStatusOrderByStartedAtAsc(12L, "STARTED")).thenReturn(List.of());
    when(sessions.findTop100ByAcademyIdAndStatusInOrderByStartedAtDescIdDesc(12L, List.of("STOPPED", "UNCONFIRMED", "CANCELLED")))
        .thenReturn(List.of());
    when(sessions.dailyCollections(any(), any(), any())).thenReturn(List.of());

    var result = service.dashboard(jwt);

    assertEquals(List.of(), result.running());
    assertEquals(List.of(), result.recent());
    assertEquals(List.of(), result.facilities());
    assertEquals(List.of(), result.facilitySummary());
    assertEquals(List.of(), result.collectionsBySport());
    assertEquals(BigDecimal.ZERO, result.chargesToday());
    assertEquals(BigDecimal.ZERO, result.collectedToday());
    assertEquals(BigDecimal.ZERO, result.outstandingToday());
  }

  @Test
  void dashboardDoesNotOverrideWritableAuthorizationTransactionWithReadOnly() throws Exception {
    assertNull(RunningSessionService.class.getMethod("dashboard", Jwt.class).getAnnotation(Transactional.class));
    assertEquals(false, RunningSessionService.class.getAnnotation(Transactional.class).readOnly());
  }

  @Test
  void dashboardGroupsActiveFacilitiesAndDailyCollectionsByAcademySport() {
    stubOwner();
    AcademySport badminton = new AcademySport();
    badminton.setId(4L); badminton.setAcademyId(12L); badminton.setName("Badminton");
    badminton.setCatalogueCode("BADMINTON"); badminton.setActive(true); badminton.setSessions(true);
    AcademyPlayArea court = new AcademyPlayArea();
    court.setId(6L); court.setAcademyId(12L); court.setSportId(4L); court.setCode("C1");
    court.setName("Court 1"); court.setDisplayName("Court 1"); court.setFacilityType("COURT");
    court.setActive(true); court.setHourlyRate(new BigDecimal("600.00"));
    AcademyPlayArea inactiveTable = new AcademyPlayArea();
    inactiveTable.setId(7L); inactiveTable.setAcademyId(12L); inactiveTable.setSportId(3L);
    inactiveTable.setCode("E2"); inactiveTable.setFacilityType("ENGLISH");
    inactiveTable.setActive(false); inactiveTable.setHourlyRate(new BigDecimal("100.00"));
    when(facilities.findByAcademyIdOrderByName(12L)).thenReturn(List.of(facility, inactiveTable, court));
    when(sports.findByAcademyIdOrderByName(12L)).thenReturn(List.of(sport, badminton));
    when(facilities.findById(5L)).thenReturn(Optional.of(facility));
    when(sports.findById(4L)).thenReturn(Optional.of(badminton));
    when(sports.findById(3L)).thenReturn(Optional.of(sport));
    when(facilities.findById(6L)).thenReturn(Optional.of(court));
    AcademyRunningSession snookerSession = running(30, 0);
    AcademyRunningSession badmintonSession = running(30, 0);
    badmintonSession.setFacilityId(6L); badmintonSession.setSportId(4L);
    when(sessions.findByAcademyIdAndStatusOrderByStartedAtAsc(12L, "RESERVED")).thenReturn(List.of());
    when(sessions.findByAcademyIdAndStatusOrderByStartedAtAsc(12L, "STARTED"))
        .thenReturn(List.of(snookerSession, badmintonSession));
    when(sessions.findTop100ByAcademyIdAndStatusInOrderByStartedAtDescIdDesc(12L, List.of("STOPPED", "UNCONFIRMED", "CANCELLED")))
        .thenReturn(List.of());
    when(sessions.dailyCollections(any(), any(), any())).thenReturn(List.of(
        collection(3L, "Snooker", "SNOOKER", "100.00", "75.00"),
        collection(4L, "Badminton", "BADMINTON", "50.00", "50.00")));

    var result = service.dashboard(jwt);

    assertEquals(2, result.facilitySummary().size());
    assertEquals("SNOOKER", result.facilitySummary().get(0).sport());
    assertEquals(1L, result.facilitySummary().get(0).activeFacilities());
    assertEquals(1L, result.facilitySummary().get(0).occupiedFacilities());
    assertEquals(0L, result.facilitySummary().get(0).availableFacilities());
    assertEquals("BADMINTON", result.facilitySummary().get(1).sport());
    assertEquals(1L, result.facilitySummary().get(1).activeFacilities());
    assertEquals(1L, result.facilitySummary().get(1).occupiedFacilities());
    assertEquals(0L, result.facilitySummary().get(1).availableFacilities());
    assertEquals(2, result.running().size());
    assertEquals(new BigDecimal("150.00"), result.chargesToday());
    assertEquals(BigDecimal.ZERO, result.collectedToday());
    assertEquals(BigDecimal.ZERO, result.outstandingToday());
    assertEquals(BigDecimal.ZERO, result.collectionsBySport().get(0).pending());
    assertEquals(BigDecimal.ZERO, result.collectionsBySport().get(1).pending());
  }

  @Test void reserveHoldsFacilityWithoutBillableTime() {
    stubOwner();
    when(facilityService.requireEligibleLocked(jwt,12L,5L)).thenReturn(facility);
    when(sports.findById(3L)).thenReturn(Optional.of(sport));
    when(sessions.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
    var result=service.reserve(jwt,new RunningSessionContracts.Reserve(5L," Guest ",LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")).plusHours(1),LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")).plusHours(2)));
    assertEquals("RESERVED",result.status()); assertEquals("Guest",result.playerName());
    assertEquals(0L,result.durationSeconds()); assertEquals(BigDecimal.ZERO,result.currentAmount());
    assertEquals("NOT_DUE",result.paymentStatus());
  }

  @Test void reservationPreventsDirectStartAndDuplicateReservation() {
    stubOwner();
    when(facilityService.requireEligibleLocked(jwt,12L,5L)).thenReturn(facility);
    when(sports.findById(3L)).thenReturn(Optional.of(sport));
    var held=running(0,0);held.setStatus("RESERVED");held.setReservedStart(LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")).minusMinutes(1));held.setReservedEnd(LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")).plusHours(3));
    when(sessions.findByAcademyIdAndFacilityIdAndStatusIn(12L,5L,List.of("RESERVED","STARTED"))).thenReturn(List.of(held));
    assertThrows(com.arena.core.exception.ArenaOpsException.class,()->service.start(jwt,new RunningSessionContracts.Start(5L,null)));
    assertThrows(com.arena.core.exception.ArenaOpsException.class,()->service.reserve(jwt,new RunningSessionContracts.Reserve(5L,null,LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")).plusHours(1),LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")).plusHours(2))));
  }

  @Test void startingReservationResetsClockAndUsesCurrentRate() {
    stubOwner();var held=running(120,0);held.setStatus("RESERVED");held.setReservedStart(LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")).minusMinutes(1));held.setReservedEnd(LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")).plusHours(1));
    when(sessions.findByIdAndAcademyIdForUpdate(8L,12L)).thenReturn(Optional.of(held));
    when(facilityService.requireEligibleLocked(jwt,12L,5L)).thenReturn(facility);
    when(sports.findById(3L)).thenReturn(Optional.of(sport));
    when(sessions.saveAndFlush(any())).thenAnswer(i->i.getArgument(0));
    var result=service.startReserved(jwt,8L);
    assertEquals("STARTED",result.status());assertTrue(result.durationSeconds()<2);
    assertEquals(facility.getHourlyRate(),result.ratePerHour());
    assertThrows(com.arena.core.exception.ArenaOpsException.class,()->service.startReserved(jwt,8L));
  }

  @Test void cancellingReservationReleasesWithoutCharging() {
    stubOwner();var held=running(120,0);held.setStatus("RESERVED");held.setReservedStart(LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")).minusMinutes(1));held.setReservedEnd(LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")).plusHours(1));
    when(sessions.findByIdAndAcademyIdForUpdate(8L,12L)).thenReturn(Optional.of(held));
    when(facilities.findById(5L)).thenReturn(Optional.of(facility));
    when(sports.findById(3L)).thenReturn(Optional.of(sport));
    when(sessions.saveAndFlush(any())).thenAnswer(i->i.getArgument(0));
    var result=service.cancelReservation(jwt,8L);
    assertEquals("CANCELLED",result.status());assertEquals(BigDecimal.ZERO,result.totalAmount());assertEquals(0L,result.durationSeconds());
  }

  @Test void reservationMutationsCannotReadAnotherAcademysSession() {
    stubOwner();
    assertThrows(com.arena.core.exception.ArenaOpsException.class,()->service.startReserved(jwt,99L));
    assertThrows(com.arena.core.exception.ArenaOpsException.class,()->service.cancelReservation(jwt,99L));
  }

  @Test void partialCollectionRemainsUnsettledUntilFullyPaid() {
    stubOwner();var session=running(30,0);session.setStatus("STOPPED");session.setTotalAmount(new BigDecimal("100"));session.setPaidAmount(new BigDecimal("20"));
    when(sessions.findByIdAndAcademyIdForUpdate(8L,12L)).thenReturn(Optional.of(session));
    when(facilities.findById(5L)).thenReturn(Optional.of(facility));when(sports.findById(3L)).thenReturn(Optional.of(sport));
    when(sessions.save(any())).thenAnswer(i->i.getArgument(0));
    var partial=service.settle(jwt,8L,new BigDecimal("60"));
    assertEquals("UNCONFIRMED",partial.status());assertEquals(new BigDecimal("40"),partial.outstandingAmount());
    assertThrows(com.arena.core.exception.ArenaOpsException.class,()->service.settle(jwt,8L,new BigDecimal("10")));
    assertEquals("STOPPED",service.settle(jwt,8L,new BigDecimal("100")).status());
  }

  @Test void timedReservationsRejectOverlapButAllowAdjacentWindows() {
    stubOwner();var from=LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")).plusHours(2);
    var held=running(0,0);held.setStatus("RESERVED");held.setReservedStart(from);held.setReservedEnd(from.plusHours(1));
    when(facilityService.requireEligibleLocked(jwt,12L,5L)).thenReturn(facility);when(sports.findById(3L)).thenReturn(Optional.of(sport));
    when(sessions.findByAcademyIdAndFacilityIdAndStatusIn(12L,5L,List.of("RESERVED","STARTED"))).thenReturn(List.of(held));
    when(sessions.saveAndFlush(any())).thenAnswer(i->i.getArgument(0));
    assertThrows(com.arena.core.exception.ArenaOpsException.class,()->service.reserve(jwt,new RunningSessionContracts.Reserve(5L,"Guest",from.plusMinutes(30),from.plusMinutes(90))));
    assertEquals("RESERVED",service.reserve(jwt,new RunningSessionContracts.Reserve(5L,"Next",from.plusHours(1),from.plusHours(2))).status());
    assertEquals("RESERVED",service.reserve(jwt,new RunningSessionContracts.Reserve(5L,"Before",from.minusHours(1),from)).status());
    assertThrows(com.arena.core.exception.ArenaOpsException.class,()->service.start(jwt,new RunningSessionContracts.Start(5L,null,from.plusMinutes(10))));
    assertEquals("STARTED",service.start(jwt,new RunningSessionContracts.Start(5L,null,from)).status());
  }

  @Test void invalidOrPastReservationWindowIsRejected() {
    stubOwner();when(facilityService.requireEligibleLocked(jwt,12L,5L)).thenReturn(facility);when(sports.findById(3L)).thenReturn(Optional.of(sport));
    var now=LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata"));
    assertThrows(com.arena.core.exception.ArenaOpsException.class,()->service.reserve(jwt,new RunningSessionContracts.Reserve(5L,null,now.minusHours(1),now.plusHours(1))));
    assertThrows(com.arena.core.exception.ArenaOpsException.class,()->service.reserve(jwt,new RunningSessionContracts.Reserve(5L,null,now.plusHours(1),now.plusHours(1))));
  }

  @Test void reservationCannotStartBeforeItsSlot() {
    stubOwner();var held=running(0,0);held.setStatus("RESERVED");held.setReservedStart(LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")).plusHours(1));held.setReservedEnd(held.getReservedStart().plusHours(1));
    when(sessions.findByIdAndAcademyIdForUpdate(8L,12L)).thenReturn(Optional.of(held));when(facilityService.requireEligibleLocked(jwt,12L,5L)).thenReturn(facility);
    assertThrows(com.arena.core.exception.ArenaOpsException.class,()->service.startReserved(jwt,8L));
  }

  @Test void secondBasedSnookerExampleAndNoPauseHistoryKeepOriginalAmounts() {
    var start=LocalDateTime.of(2026,10,30,15,25,0,123456000);var value=running(0,0);value.setStartedAt(start);
    long seconds=org.springframework.test.util.ReflectionTestUtils.invokeMethod(service,"billableSeconds",value,start.plusSeconds(933));
    BigDecimal bill=org.springframework.test.util.ReflectionTestUtils.invokeMethod(service,"amount",new BigDecimal("220.00"),seconds,"SNOOKER");
    assertEquals(933L,seconds);assertEquals(0L,value.getTotalPausedSeconds());assertEquals(new BigDecimal("57"),bill);
  }

  @Test void onePauseMatchesPreciseWallClockInvariant() {
    var start=LocalDateTime.of(2026,10,30,15,25,0,123456000);var value=running(0,0);value.setStartedAt(start);
    value.setPausedAt(start.plusSeconds(120).plusNanos(100000000));
    org.springframework.test.util.ReflectionTestUtils.invokeMethod(service,"applyOpenPause",value,start.plusSeconds(240).plusNanos(100000000));
    var end=start.plusSeconds(362).plusNanos(500000000);
    long seconds=org.springframework.test.util.ReflectionTestUtils.invokeMethod(service,"billableSeconds",value,end);
    assertEquals(242L,seconds);assertEquals(120L,value.getTotalPausedSeconds());assertNull(value.getPausedAt());
    assertPreciseInvariant(value,end,seconds);
  }

  @Test void multiplePauseCyclesAccumulateWholeSecondsWithoutChangingRounding() {
    var start=LocalDateTime.of(2026,10,30,15,25,0,123456000);var value=running(0,0);value.setStartedAt(start);
    value.setPausedAt(start.plusSeconds(60).plusNanos(250000000));
    org.springframework.test.util.ReflectionTestUtils.invokeMethod(service,"applyOpenPause",value,start.plusSeconds(120).plusNanos(750000000));
    value.setPausedAt(start.plusSeconds(200).plusNanos(900000000));
    org.springframework.test.util.ReflectionTestUtils.invokeMethod(service,"applyOpenPause",value,start.plusSeconds(260).plusNanos(400000000));
    var end=start.plusSeconds(500).plusNanos(900000000);
    long seconds=org.springframework.test.util.ReflectionTestUtils.invokeMethod(service,"billableSeconds",value,end);
    // Existing policy truncates each pause: 60.5s + 59.5s becomes 60 + 59 stored seconds.
    assertEquals(119L,value.getTotalPausedSeconds());assertEquals(381L,seconds);
    BigDecimal bill=org.springframework.test.util.ReflectionTestUtils.invokeMethod(service,"amount",new BigDecimal("220.00"),seconds,"SNOOKER");
    assertEquals(new BigDecimal("23"),bill);assertPreciseInvariant(value,end,seconds);
  }

  @Test void stoppingWhilePausedPersistsCompletedAndOpenPauseTime() {
    var reference=LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata"));
    var value=pauseFixture(reference.minusSeconds(600));value.setTotalPausedSeconds(90L);value.setPausedAt(reference.minusSeconds(120));
    var pausedAt=value.getPausedAt();
    var result=service.stop(jwt,8L,new Stop(new BigDecimal("23"),null,null,false,false));
    assertEquals(90L+java.time.Duration.between(pausedAt,value.getStoppedAt()).getSeconds(),result.totalPausedSeconds());
    assertEquals(390L,result.durationSeconds());assertNull(value.getPausedAt());
    assertEquals(new BigDecimal("23"),result.totalAmount());assertEquals(new BigDecimal("23"),result.paidAmount());
    assertEquals(BigDecimal.ZERO,result.outstandingAmount());org.mockito.Mockito.verify(sessions).save(value);
    assertPreciseInvariant(value,value.getEndedAt(),result.durationSeconds());
  }

  @Test void resumedSessionUsesSavedPauseStateAndTotals() {
    var reference=LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata"));var value=pauseFixture(reference.minusSeconds(600));
    value.setTotalPausedSeconds(90L);value.setPausedAt(reference.minusSeconds(120));var pausedAt=value.getPausedAt();
    var before=service.resume(jwt,8L);assertNull(value.getPausedAt());assertNull(before.pausedAt());
    assertTrue(before.totalPausedSeconds()>=210L);org.mockito.Mockito.verify(sessions).save(value);
    assertTrue(java.time.Duration.between(pausedAt,LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata"))).getSeconds()>=120L);
  }

  @Test void shortCancelledSessionRetainsItsRecordedPauseDuration() {
    var reference=LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata"));var value=pauseFixture(reference.minusSeconds(50));
    value.setPausedAt(reference.minusSeconds(45));var pausedAt=value.getPausedAt();
    var result=service.stop(jwt,8L,new Stop(BigDecimal.ZERO,null,null,false,false));
    assertEquals("CANCELLED",result.status());assertEquals(5L,result.durationSeconds());
    assertEquals(java.time.Duration.between(pausedAt,value.getStoppedAt()).getSeconds(),result.totalPausedSeconds());
    assertEquals(BigDecimal.ZERO,result.totalAmount());assertEquals(BigDecimal.ZERO,result.paidAmount());
    assertPreciseInvariant(value,value.getEndedAt(),result.durationSeconds());
  }

  private AcademyRunningSession pauseFixture(LocalDateTime start) {
    stubOwner();var value=running(0,0);value.setStartedAt(start);value.setRatePerHour(new BigDecimal("220.00"));
    when(sessions.findByIdAndAcademyIdForUpdate(8L,12L)).thenReturn(Optional.of(value));
    when(sessions.save(any())).thenAnswer(i->i.getArgument(0));
    when(facilities.findById(5L)).thenReturn(Optional.of(facility));when(sports.findById(3L)).thenReturn(Optional.of(sport));return value;
  }

  private void assertPreciseInvariant(AcademyRunningSession value,LocalDateTime end,long billable) {
    var elapsed=java.time.Duration.between(value.getStartedAt(),end);
    assertEquals(elapsed.getSeconds(),billable+value.getTotalPausedSeconds());
    assertTrue(elapsed.minusSeconds(billable+value.getTotalPausedSeconds()).toNanos()<1000000000L);
  }

  private AcademyRunningSession running(int elapsedMinutes, int pausedMinutes) {
    AcademyRunningSession value = new AcademyRunningSession();
    value.setId(8L); value.setAcademyId(12L); value.setSportId(3L); value.setFacilityId(5L); value.setStatus("STARTED");
    value.setStartedAt(LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")).minusMinutes(elapsedMinutes)); value.setPausedAt(pausedMinutes > 0 ? LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")).minusMinutes(pausedMinutes) : null);
    value.setRatePerHour(new BigDecimal("100.00")); value.setTotalPausedSeconds(0L); value.setPaidAmount(BigDecimal.ZERO);
    return value;
  }

  private AcademyRunningSessionRepository.DailyCollectionTotals collection(Long sportId, String name, String code,
      String charged, String collected) {
    return new AcademyRunningSessionRepository.DailyCollectionTotals() {
      @Override public Long getSportId() { return sportId; }
      @Override public String getSportName() { return name; }
      @Override public String getSportCode() { return code; }
      @Override public BigDecimal getCharged() { return new BigDecimal(charged); }
      @Override public BigDecimal getCollected() { return new BigDecimal(collected); }
    };
  }

  private void stubOwner() {
    AppUserEntity owner = AppUserEntity.builder().id(1L).username("owner").customerId(12L).build();
    when(authorization.actor(jwt)).thenReturn(new AcademyAuthorization.Actor(owner, null, Set.of("OWNER")));
  }

  @Test void inactiveSportFacilitiesAreUnavailableWithoutChangingIndividualStatus(){
    stubOwner();sport.setActive(false);when(sports.findById(3L)).thenReturn(Optional.of(sport));
    when(facilities.findByAcademyIdOrderByName(12L)).thenReturn(List.of(facility));
    when(sports.findByAcademyIdOrderByName(12L)).thenReturn(List.of(sport));
    var dashboard=service.dashboard(jwt);assertEquals(1,dashboard.facilities().size());assertEquals(false,dashboard.facilities().get(0).available());assertTrue(facility.isActive());assertTrue(dashboard.facilitySummary().isEmpty());
  }
}