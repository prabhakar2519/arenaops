package com.arena.core.training;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrainingSessionRepository extends JpaRepository<TrainingSession, Long> {
  Optional<TrainingSession> findByAcademyIdAndBatchIdAndSessionDateAndStartTime(Long academyId, Long batchId, LocalDate date, LocalTime time);
  Optional<TrainingSession> findByIdAndAcademyId(Long id, Long academyId);
  List<TrainingSession> findByAcademyIdAndSessionDateOrderByStartTime(Long academyId, LocalDate date);
}