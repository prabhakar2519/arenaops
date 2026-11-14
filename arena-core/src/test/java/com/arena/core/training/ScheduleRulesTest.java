package com.arena.core.training;
import com.arena.core.exception.*;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ScheduleRulesTest {
 private BatchSchedule entry(int day,String start,String end,String from,String to) {
  BatchSchedule s=new BatchSchedule();s.setDayOfWeek(day);s.setStartTime(LocalTime.parse(start));s.setEndTime(LocalTime.parse(end));s.setEffectiveFrom(LocalDate.parse(from));s.setEffectiveTo(to==null?null:LocalDate.parse(to));return s;
 }
 @Test void adjacentTimesAndDifferentDaysAreAllowed() {
  var a=entry(1,"09:00","10:00","2026-01-01",null);
  assertFalse(ScheduleRules.overlaps(a,entry(1,"10:00","11:00","2026-01-01",null)));
  assertFalse(ScheduleRules.overlaps(a,entry(2,"09:30","10:30","2026-01-01",null)));
 }
 @Test void dateIntersectionMustContainTheWeekday() {
  var a=entry(1,"09:00","10:00","2026-01-01","2026-01-05");
  assertFalse(ScheduleRules.overlaps(a,entry(1,"09:30","10:30","2026-01-01","2026-01-04")));
  assertTrue(ScheduleRules.overlaps(a,entry(1,"09:30","10:30","2026-01-05","2026-01-12")));
  assertFalse(ScheduleRules.overlaps(a,entry(1,"09:30","10:30","2026-01-06",null)));
 }
 @Test void duplicateAndOverlappingEntriesAreRejected() {
  var a=entry(1,"09:00","10:00","2026-01-01",null);
  assertEquals(ErrorCode.TRAINING_SCHEDULE_CONFLICT,assertThrows(ArenaOpsException.class,()->ScheduleRules.noInternalOverlap(List.of(a,a))).getErrorCode());
 }
 @Test void invalidTimesAndDatesAndEmptyRecurrencesAreRejected() {
  TrainingBatch b=new TrainingBatch();b.setStartDate(LocalDate.parse("2026-01-01"));b.setEndDate(LocalDate.parse("2026-01-31"));
  for(var s:List.of(entry(1,"10:00","09:00","2026-01-01","2026-01-31"),entry(1,"10:00","10:00","2026-01-01","2026-01-31"),entry(1,"09:00","10:00","2025-12-31","2026-01-31"),entry(1,"09:00","10:00","2026-01-01",null),entry(1,"09:00","10:00","2026-01-01","2026-01-04"),entry(1,"09:00","10:00","2026-01-20","2026-01-10")))
   assertThrows(ArenaOpsException.class,()->ScheduleRules.validate(s,b));
  ScheduleRules.validate(entry(1,"09:00","10:00","2026-01-01","2026-01-31"),b);
 }
}
