package com.arena.core.checkin;
import org.springframework.data.jpa.repository.*;import org.springframework.data.repository.query.Param;
public interface FacilityPermanentQrRepository extends JpaRepository<FacilityPermanentQr,Long>{
 @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE) @Query("select q from FacilityPermanentQr q where q.publicId=:id") java.util.Optional<FacilityPermanentQr> byPublicId(@Param("id")String id);
 @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE) @Query("select q from FacilityPermanentQr q where q.facilityId=:id and q.academyId=:academy") java.util.Optional<FacilityPermanentQr> locked(@Param("academy")Long academy,@Param("id")Long id);
}
