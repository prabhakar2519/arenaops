package com.arena.core.publicclub;
import org.springframework.data.jpa.repository.*;import org.springframework.data.repository.query.Param;
public interface ClubPublicSiteRepository extends JpaRepository<ClubPublicSite,Long>{
 @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE) @Query("select s from ClubPublicSite s where s.academyId=:academy") java.util.Optional<ClubPublicSite> locked(@Param("academy")Long academy);
}
