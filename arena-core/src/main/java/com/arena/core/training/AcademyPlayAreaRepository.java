package com.arena.core.training;
public interface AcademyPlayAreaRepository extends org.springframework.data.jpa.repository.JpaRepository<AcademyPlayArea,Long> {
 java.util.List<AcademyPlayArea> findByAcademyIdOrderByName(Long academyId);
 java.util.List<AcademyPlayArea> findByAcademyIdAndSportIdOrderById(Long academyId,Long sportId);
 @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
 @org.springframework.data.jpa.repository.Query("select f from AcademyPlayArea f where f.academyId=:academy and f.id=:id")
 java.util.Optional<AcademyPlayArea> locked(@org.springframework.data.repository.query.Param("academy") Long academy,@org.springframework.data.repository.query.Param("id") Long id);
}
