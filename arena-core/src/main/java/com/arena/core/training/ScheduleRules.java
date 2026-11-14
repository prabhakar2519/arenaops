package com.arena.core.training;

import com.arena.core.exception.*;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

/** Weekly recurrences intersect only if a shared weekday occurs in the common date interval. */
public final class ScheduleRules {
 private ScheduleRules() {}
 public static void validate(BatchSchedule s, TrainingBatch b) {
  if (s.getDayOfWeek()<1 || s.getDayOfWeek()>7 || !s.getEndTime().isAfter(s.getStartTime())
   || s.getEffectiveFrom().isBefore(b.getStartDate())
   || (s.getEffectiveTo()!=null && s.getEffectiveTo().isBefore(s.getEffectiveFrom()))
   || (b.getEndDate()!=null && (s.getEffectiveTo()==null || s.getEffectiveTo().isAfter(b.getEndDate()))))
    throw new ArenaOpsException(ErrorCode.TRAINING_SCHEDULE_INVALID);
  LocalDate first=s.getEffectiveFrom().with(TemporalAdjusters.nextOrSame(DayOfWeek.of(s.getDayOfWeek())));
  if (s.getEffectiveTo()!=null && first.isAfter(s.getEffectiveTo()))
   throw new ArenaOpsException(ErrorCode.TRAINING_SCHEDULE_INVALID);
 }
 public static boolean overlaps(BatchSchedule a, BatchSchedule b) {
  if (a.getDayOfWeek()!=b.getDayOfWeek() || !a.getStartTime().isBefore(b.getEndTime()) || !b.getStartTime().isBefore(a.getEndTime())) return false;
  LocalDate from=a.getEffectiveFrom().isAfter(b.getEffectiveFrom())?a.getEffectiveFrom():b.getEffectiveFrom();
  LocalDate aTo=a.getEffectiveTo()==null?LocalDate.MAX:a.getEffectiveTo();
  LocalDate bTo=b.getEffectiveTo()==null?LocalDate.MAX:b.getEffectiveTo();
  LocalDate to=aTo.isBefore(bTo)?aTo:bTo;
  return !from.isAfter(to) && !from.with(TemporalAdjusters.nextOrSame(DayOfWeek.of(a.getDayOfWeek()))).isAfter(to);
 }
 public static void noInternalOverlap(List<BatchSchedule> schedules) {
  for(int i=0;i<schedules.size();i++) for(int j=i+1;j<schedules.size();j++)
   if(overlaps(schedules.get(i),schedules.get(j))) throw new ArenaOpsException(ErrorCode.TRAINING_SCHEDULE_CONFLICT);
 }
}
