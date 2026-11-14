package com.arena.core.students;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AcademyStudentSportRepository extends JpaRepository<AcademyStudentSport, Long> {
  List<AcademyStudentSport> findByStudentIdAndAcademyIdOrderBySportId(Long studentId, Long academyId);
  void deleteAllByStudentIdAndAcademyId(Long studentId, Long academyId);
}