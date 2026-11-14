package com.arena.core.checkin;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface FacilityQrChallengeRepository extends JpaRepository<FacilityQrChallenge,Long> {
 @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
 @Query("select q from FacilityQrChallenge q where q.tokenHash=:hash")
 Optional<FacilityQrChallenge> locked(@Param("hash")String hash);
 @Modifying @Query("delete from FacilityQrChallenge q where q.expiresAt<:cutoff and not exists(select c.id from FacilityCustomerCheckin c where c.challengeId=q.id)")
 int purge(@Param("cutoff")java.time.Instant cutoff);
}
