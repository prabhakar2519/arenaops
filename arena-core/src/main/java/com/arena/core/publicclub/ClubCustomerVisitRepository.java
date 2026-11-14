package com.arena.core.publicclub;
public interface ClubCustomerVisitRepository extends org.springframework.data.jpa.repository.JpaRepository<ClubCustomerVisit,Long>{
 java.util.Optional<ClubCustomerVisit> findByAcademyIdAndSessionId(Long academy,Long session);
 boolean existsBySessionId(Long id);
 java.util.List<ClubCustomerVisit> findByAcademyIdAndCustomerIdOrderByStartedAtDesc(Long academy,Long customer);
}
