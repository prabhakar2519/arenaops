package com.arena.core.publicclub;
import org.springframework.data.jpa.repository.*;import org.springframework.data.repository.query.Param;
public interface ClubCustomerRepository extends JpaRepository<ClubCustomer,Long>{
 java.util.Optional<ClubCustomer> findByAcademyIdAndIdentityKey(Long academy,String key);
 org.springframework.data.domain.Page<ClubCustomer> findByAcademyIdAndDeletedAtIsNullOrderByUpdatedAtDesc(Long academy,org.springframework.data.domain.Pageable page);
 @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE) @Query("select c from ClubCustomer c where c.id=:id and c.academyId=:academy")java.util.Optional<ClubCustomer> locked(@Param("academy")Long academy,@Param("id")Long id);
 java.util.List<ClubCustomer> findTop100ByDeletedAtIsNullAndUpdatedAtBefore(java.time.Instant before);
}
