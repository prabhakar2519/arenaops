package com.arena.core.sessions;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AcademyRunningSessionRepository extends JpaRepository<AcademyRunningSession, Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<AcademyRunningSession> {
  List<AcademyRunningSession> findByAcademyIdAndStatusOrderByStartedAtAsc(Long academyId, String status);
  List<AcademyRunningSession> findTop100ByAcademyIdAndStatusInOrderByStartedAtDescIdDesc(Long academyId, List<String> statuses);
  Optional<AcademyRunningSession> findByIdAndAcademyId(Long id, Long academyId);
  boolean existsByAcademyIdAndFacilityIdAndStatus(Long academyId, Long facilityId, String status);
  boolean existsByAcademyIdAndSportIdAndStatus(Long academyId, Long sportId, String status);
  List<AcademyRunningSession> findByAcademyIdAndFacilityIdAndStatusIn(Long academyId, Long facilityId, List<String> statuses);
  @Query("select session from AcademyRunningSession session where session.academyId = :academyId and (session.status = 'UNCONFIRMED' or (session.status = 'STOPPED' and session.totalAmount > session.paidAmount)) order by session.startedAt, session.id")
  List<AcademyRunningSession> findUnsettled(@Param("academyId") Long academyId);
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select session from AcademyRunningSession session where session.id = :id and session.academyId = :academyId")
  Optional<AcademyRunningSession> findByIdAndAcademyIdForUpdate(@Param("id") Long id, @Param("academyId") Long academyId);
  @Query("""
      select sport.id as sportId, sport.name as sportName, sport.catalogueCode as sportCode,
        coalesce(sum(case when session.status in ('STOPPED','UNCONFIRMED')
          then coalesce(session.totalAmount, 0) else 0 end), 0) as charged,
        coalesce(sum(case when session.status <> 'CANCELLED'
          then session.paidAmount else 0 end), 0) as collected
      from AcademyRunningSession session
      join AcademySport sport on sport.id = session.sportId and sport.academyId = session.academyId
      where session.academyId = :academyId and session.startedAt >= :from and session.startedAt < :to
      group by sport.id, sport.name, sport.catalogueCode
      """)
  List<DailyCollectionTotals> dailyCollections(@Param("academyId") Long academyId,
      @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

  interface DailyCollectionTotals {
    Long getSportId();
    String getSportName();
    String getSportCode();
    java.math.BigDecimal getCharged();
    java.math.BigDecimal getCollected();
  }
}