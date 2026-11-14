package com.arena.core.sessions;
public interface SessionStopQuoteRepository extends org.springframework.data.jpa.repository.JpaRepository<SessionStopQuote,Long> {
 java.util.Optional<SessionStopQuote> findByTokenHashAndAcademyId(String tokenHash,Long academyId);
 @org.springframework.data.jpa.repository.Modifying
 @org.springframework.data.jpa.repository.Query("update SessionStopQuote q set q.consumedAt=:time where q.academyId=:academy and q.sessionId=:session and q.actorId=:actor and q.consumedAt is null")
 void invalidateExisting(@org.springframework.data.repository.query.Param("academy")Long academy,@org.springframework.data.repository.query.Param("session")Long session,@org.springframework.data.repository.query.Param("actor")Long actor,@org.springframework.data.repository.query.Param("time")java.time.LocalDateTime time);
}
