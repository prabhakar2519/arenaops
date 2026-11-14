package com.arena.core.sessions;
import java.util.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface SessionDiscountRequestRepository extends JpaRepository<SessionDiscountRequest,Long> {
 @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
 @Query("select r from SessionDiscountRequest r where r.id=:id and r.academyId=:academy")
 Optional<SessionDiscountRequest> locked(@Param("academy")Long academy,@Param("id")Long id);
 List<SessionDiscountRequest> findTop100ByAcademyIdOrderByRequestedAtDesc(Long academy);
 List<SessionDiscountRequest> findTop100ByAcademyIdAndStatusOrderByRequestedAtAsc(Long academy,String status);
 List<SessionDiscountRequest> findTop100ByAcademyIdAndRequestedByIdOrderByRequestedAtDesc(Long academy,Long user);
 @Query("select coalesce(sum(r.amount),0) from SessionDiscountRequest r where r.academyId=:academy and r.requestedById=:user and r.status='APPROVED' and r.decidedAt>=:from and r.decidedAt<:to")
 BigDecimal dailyApproved(@Param("academy")Long academy,@Param("user")Long user,@Param("from")LocalDateTime from,@Param("to")LocalDateTime to);
}
