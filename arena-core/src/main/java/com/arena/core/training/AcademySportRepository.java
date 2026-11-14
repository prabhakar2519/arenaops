package com.arena.core.training;
public interface AcademySportRepository extends org.springframework.data.jpa.repository.JpaRepository<AcademySport,Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<AcademySport> {
 java.util.List<AcademySport> findByAcademyIdOrderByName(Long academyId);
}
