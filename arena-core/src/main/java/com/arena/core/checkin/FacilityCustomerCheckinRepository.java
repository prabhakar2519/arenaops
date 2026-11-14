package com.arena.core.checkin;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface FacilityCustomerCheckinRepository extends JpaRepository<FacilityCustomerCheckin,Long> {
 @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
 @Query("select c from FacilityCustomerCheckin c where c.academyId=:academy and c.id=:id")
 Optional<FacilityCustomerCheckin> locked(@Param("academy")Long academy,@Param("id")Long id);
 List<FacilityCustomerCheckin> findTop100ByAcademyIdAndStatusAndExpiresAtAfterOrderByCreatedAtAsc(Long academy,String status,Instant now);
 @Modifying @Query("update FacilityCustomerCheckin c set c.status='EXPIRED',c.customerName=null,c.mobile=null where c.status='PENDING' and c.expiresAt<=:now")
 int expire(@Param("now")Instant now);
 @Modifying @Query("update FacilityCustomerCheckin c set c.customerName=null,c.mobile=null where c.status='CONFIRMED' and c.confirmedAt<:cutoff and (c.customerName is not null or c.mobile is not null)")
 int scrub(@Param("cutoff")Instant cutoff);
 @Modifying @Query("delete from FacilityCustomerCheckin c where c.status in ('EXPIRED','CANCELLED','REJECTED') and c.expiresAt<:cutoff")
 int purge(@Param("cutoff")Instant cutoff);
 java.util.Optional<FacilityCustomerCheckin> findByAcademyIdAndFacilityIdAndRequestId(Long academy,Long facility,java.util.UUID requestId);
 long countByAcademyIdAndFacilityIdAndCreatedAtAfter(Long academy,Long facility,Instant after);
 long countByAcademyIdAndFacilityIdAndMobileHashAndCreatedAtAfter(Long academy,Long facility,String hash,Instant after);
 boolean existsByAcademyIdAndFacilityIdAndStatus(Long academy,Long facility,String status);
 @Modifying @Query("update FacilityCustomerCheckin c set c.status='EXPIRED',c.customerName=null,c.mobile=null where c.academyId=:academy and c.facilityId=:facility and c.status='PENDING' and c.expiresAt<=:now")int expireFacility(@Param("academy")Long academy,@Param("facility")Long facility,@Param("now")Instant now);
 @Modifying @Query("update FacilityCustomerCheckin c set c.customerName=null,c.mobile=null where c.academyId=:academy and c.customerId=:customer")int scrubCustomer(@Param("academy")Long academy,@Param("customer")Long customer);
}
