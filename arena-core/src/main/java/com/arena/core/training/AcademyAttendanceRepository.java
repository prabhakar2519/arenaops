package com.arena.core.training;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AcademyAttendanceRepository extends JpaRepository<AcademyAttendance, Long> {
  Optional<AcademyAttendance> findBySessionIdAndEnrollmentId(Long sessionId, Long enrollmentId);
  Optional<AcademyAttendance> findByIdAndAcademyId(Long id, Long academyId);
  List<AcademyAttendance> findByAcademyIdAndSessionIdOrderByCapturedAt(Long academyId, Long sessionId);
  List<AcademyAttendance> findByAcademyIdAndStudentIdOrderByCapturedAtDesc(Long academyId, Long studentId);
}