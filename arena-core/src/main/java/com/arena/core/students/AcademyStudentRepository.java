package com.arena.core.students;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AcademyStudentRepository extends JpaRepository<AcademyStudent, Long>, JpaSpecificationExecutor<AcademyStudent> {
  Optional<AcademyStudent> findByIdAndAcademyId(Long id, Long academyId);
}