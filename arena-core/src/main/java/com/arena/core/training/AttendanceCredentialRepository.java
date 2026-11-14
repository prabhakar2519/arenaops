package com.arena.core.training;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceCredentialRepository extends JpaRepository<AttendanceCredential, Long> {
  Optional<AttendanceCredential> findByTokenHashAndActiveTrue(String tokenHash);
  List<AttendanceCredential> findByStudentIdAndAcademyIdAndKindAndActiveTrue(Long studentId, Long academyId, String kind);
}