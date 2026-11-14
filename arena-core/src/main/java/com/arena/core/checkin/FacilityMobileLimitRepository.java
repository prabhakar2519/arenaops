package com.arena.core.checkin;
public interface FacilityMobileLimitRepository extends org.springframework.data.jpa.repository.JpaRepository<FacilityMobileLimit,Long>{
 java.util.Optional<FacilityMobileLimit> findByFacilityIdAndMobileHash(Long facility,String hash);
 @org.springframework.data.jpa.repository.Modifying @org.springframework.data.jpa.repository.Query("delete from FacilityMobileLimit m where m.blockedUntil<:before")int purge(@org.springframework.data.repository.query.Param("before")java.time.Instant before);
}
