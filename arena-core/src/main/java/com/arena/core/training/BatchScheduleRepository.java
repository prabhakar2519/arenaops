package com.arena.core.training;
public interface BatchScheduleRepository extends org.springframework.data.jpa.repository.JpaRepository<BatchSchedule,Long> {
 java.util.List<BatchSchedule> findByBatchIdAndActiveTrueOrderByDayOfWeekAscStartTimeAsc(Long batchId);
}
