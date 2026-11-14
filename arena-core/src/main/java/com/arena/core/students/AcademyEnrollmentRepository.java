package com.arena.core.students;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AcademyEnrollmentRepository extends JpaRepository<AcademyEnrollment, Long> {
  Optional<AcademyEnrollment> findByIdAndAcademyId(Long id, Long academyId);
  boolean existsByAcademyIdAndStudentIdAndBatchIdAndStatus(Long academyId, Long studentId, Long batchId, String status);
  List<AcademyEnrollment> findByAcademyIdAndBatchIdOrderByCreatedAtDesc(Long academyId, Long batchId);
  List<AcademyEnrollment> findByAcademyIdAndStudentIdOrderByCreatedAtDesc(Long academyId, Long studentId);
  long countByAcademyIdAndBatchIdAndStatus(Long academyId, Long batchId, String status);
  Optional<AcademyEnrollment> findByAcademyIdAndStudentIdAndBatchIdAndStatus(Long academyId, Long studentId, Long batchId, String status);
}