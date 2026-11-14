package com.arena.core.publicclub;
import java.time.*;import java.util.*;import org.springframework.data.jpa.repository.*;import org.springframework.data.repository.query.Param;
public interface ClubReservationRequestRepository extends JpaRepository<ClubReservationRequest,Long>{
 Optional<ClubReservationRequest> findByAcademyIdAndRequestId(Long academy,UUID id);
 Optional<ClubReservationRequest> findByAcademyIdAndSessionId(Long academy,Long id);
 List<ClubReservationRequest> findTop100ByAcademyIdAndStatusAndExpiresAtAfterOrderByCreatedAtAsc(Long academy,String status,Instant now);
 long countByAcademyIdAndMobileHashAndCreatedAtAfter(Long academy,String hash,Instant after);
 long countByAcademyIdAndFacilityIdAndCreatedAtAfter(Long academy,Long facility,Instant after);
 @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE) @Query("select r from ClubReservationRequest r where r.id=:id and r.academyId=:academy")Optional<ClubReservationRequest> locked(@Param("academy")Long academy,@Param("id")Long id);
 @Modifying @Query("update ClubReservationRequest r set r.customerName=null,r.mobile=null where r.academyId=:academy and r.customerId=:customer")int scrubCustomer(@Param("academy")Long academy,@Param("customer")Long customer);
 @Modifying @Query("update ClubReservationRequest r set r.status='EXPIRED',r.customerName=null,r.mobile=null where r.status='PENDING' and r.expiresAt<=:now")int expire(@Param("now")Instant now);
 @Modifying @Query("update ClubReservationRequest r set r.customerName=null,r.mobile=null where r.status<>'PENDING' and r.expiresAt<:before")int scrub(@Param("before")Instant before);
 @Modifying @Query("delete from ClubReservationRequest r where r.status in ('EXPIRED','REJECTED') and r.expiresAt<:before")int purge(@Param("before")Instant before);
}
