package com.arena.core.training;

import static com.arena.core.training.TrainingOperationsContracts.*;

import com.arena.core.exception.ArenaOpsException;
import com.arena.core.exception.ErrorCode;
import com.arena.core.students.AcademyEnrollment;
import com.arena.core.students.AcademyEnrollmentRepository;
import com.arena.core.students.AcademyStudent;
import com.arena.core.students.AcademyStudentRepository;
import com.arena.core.students.AcademyStudentSportRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TrainingOperationsService {
  private final AcademyAuthorization authorization;
  private final AcademyStudentRepository students;
  private final AcademyStudentSportRepository studentSports;
  private final AcademyEnrollmentRepository enrollments;
  private final TrainingBatchRepository batches;
  private final BatchScheduleRepository schedules;
  private final TrainingSessionRepository sessions;

  public TrainingOperationsService(AcademyAuthorization authorization, AcademyStudentRepository students,
      AcademyStudentSportRepository studentSports, AcademyEnrollmentRepository enrollments,
      TrainingBatchRepository batches, BatchScheduleRepository schedules, TrainingSessionRepository sessions) {
    this.authorization = authorization;
    this.students = students;
    this.studentSports = studentSports;
    this.enrollments = enrollments;
    this.batches = batches;
    this.schedules = schedules;
    this.sessions = sessions;
  }

  public EnrollmentView enroll(Jwt jwt, Enroll request) {
    var actor = authorization.manage(jwt);
    Long academyId = actor.academyId();
    AcademyStudent student = student(request.studentId(), academyId);
    if (!"ACTIVE".equals(student.getStatus())) throw new ArenaOpsException(ErrorCode.INVALID_STATE);
    TrainingBatch batch = batches.findByIdAndAcademyIdForUpdate(request.batchId(), academyId)
      .orElseThrow(() -> new ArenaOpsException(ErrorCode.RESOURCE_NOT_FOUND));
    if (!"ACTIVE".equals(batch.getStatus()) || request.endDate() != null && request.endDate().isBefore(request.startDate())) {
      throw new ArenaOpsException(ErrorCode.INVALID_STATE);
    }
    boolean matchingSport = studentSports.findByStudentIdAndAcademyIdOrderBySportId(student.getId(), academyId)
        .stream().anyMatch(link -> Objects.equals(link.getSportId(), batch.getSportId()));
    if (!matchingSport) throw new ArenaOpsException(ErrorCode.INVALID_STATE);
    if (enrollments.existsByAcademyIdAndStudentIdAndBatchIdAndStatus(academyId, student.getId(), batch.getId(), "ACTIVE")) {
      throw new ArenaOpsException(ErrorCode.CONFLICT);
    }
    if (batch.getCapacity() != null && enrollments.countByAcademyIdAndBatchIdAndStatus(academyId, batch.getId(), "ACTIVE") >= batch.getCapacity()) {
      throw new ArenaOpsException(ErrorCode.CONFLICT);
    }
    AcademyEnrollment enrollment = new AcademyEnrollment();
    enrollment.setAcademyId(academyId);
    enrollment.setStudentId(student.getId());
    enrollment.setBatchId(batch.getId());
    enrollment.setStartDate(request.startDate());
    enrollment.setEndDate(request.endDate());
    enrollment.setStatus("ACTIVE");
    enrollment.setCreatedBy(actor.user().getUsername());
    enrollment.setUpdatedBy(actor.user().getUsername());
    return enrollmentView(enrollments.saveAndFlush(enrollment), student, batch);
  }

  @Transactional(readOnly = true)
  public List<EnrollmentView> batchEnrollments(Jwt jwt, Long batchId) {
    Long academyId = authorization.manage(jwt).academyId();
    TrainingBatch batch = batch(batchId, academyId);
    return enrollments.findByAcademyIdAndBatchIdOrderByCreatedAtDesc(academyId, batchId).stream()
        .map(enrollment -> enrollmentView(enrollment, student(enrollment.getStudentId(), academyId), batch)).toList();
  }

  @Transactional(readOnly = true)
  public List<EnrollmentView> studentEnrollments(Jwt jwt, Long studentId) {
    Long academyId = authorization.manage(jwt).academyId();
    student(studentId, academyId);
    return enrollments.findByAcademyIdAndStudentIdOrderByCreatedAtDesc(academyId, studentId).stream()
        .map(enrollment -> enrollmentView(enrollment, student(enrollment.getStudentId(), academyId), batch(enrollment.getBatchId(), academyId))).toList();
  }

  public EnrollmentView endEnrollment(Jwt jwt, Long enrollmentId, EndEnrollment request) {
    var actor = authorization.manage(jwt);
    Long academyId = actor.academyId();
    AcademyEnrollment enrollment = enrollments.findByIdAndAcademyId(enrollmentId, academyId)
        .orElseThrow(() -> new ArenaOpsException(ErrorCode.RESOURCE_NOT_FOUND));
    if (!"ACTIVE".equals(enrollment.getStatus())) throw new ArenaOpsException(ErrorCode.INVALID_STATE);
    LocalDate endDate = request == null || request.endDate() == null ? LocalDate.now() : request.endDate();
    if (endDate.isBefore(enrollment.getStartDate())) throw new ArenaOpsException(ErrorCode.INVALID_REQUEST);
    enrollment.setEndDate(endDate);
    enrollment.setStatus("ENDED");
    enrollment.setUpdatedBy(actor.user().getUsername());
    return enrollmentView(enrollments.save(enrollment), student(enrollment.getStudentId(), academyId), batch(enrollment.getBatchId(), academyId));
  }

  public SessionView resolveSession(Jwt jwt, ResolveSession request) {
    var actor = authorization.actor(jwt);
    Long academyId = actor.academyId();
    TrainingBatch batch = batch(request.batchId(), academyId);
    if (!actor.manager() && (actor.membership() == null || !actor.roles().contains("INSTRUCTOR")
        || !batch.getCoachIds().contains(actor.membership().getId()))) {
      throw new ArenaOpsException(ErrorCode.ACCESS_DENIED);
    }
    LocalDate date = request.date();
    if (!"ACTIVE".equals(batch.getStatus()) || date.isBefore(batch.getStartDate()) || batch.getEndDate() != null && date.isAfter(batch.getEndDate())) {
      throw new ArenaOpsException(ErrorCode.INVALID_STATE);
    }
    BatchSchedule schedule = schedules.findByBatchIdAndActiveTrueOrderByDayOfWeekAscStartTimeAsc(batch.getId()).stream()
        .filter(value -> value.getDayOfWeek() == date.getDayOfWeek().getValue())
        .filter(value -> value.getEffectiveFrom() == null || !date.isBefore(value.getEffectiveFrom()))
        .filter(value -> value.getEffectiveTo() == null || !date.isAfter(value.getEffectiveTo()))
        .findFirst().orElseThrow(() -> new ArenaOpsException(ErrorCode.RESOURCE_NOT_FOUND));
    TrainingSession occurrence = sessions.findByAcademyIdAndBatchIdAndSessionDateAndStartTime(academyId, batch.getId(), date, schedule.getStartTime())
        .orElseGet(() -> {
          TrainingSession created = new TrainingSession();
          created.setAcademyId(academyId);
          created.setBatchId(batch.getId());
          created.setSessionDate(date);
          created.setStartTime(schedule.getStartTime());
          created.setEndTime(schedule.getEndTime());
          created.setCreatedBy(actor.user().getUsername());
          created.setUpdatedBy(actor.user().getUsername());
          return sessions.saveAndFlush(created);
        });
    return sessionView(occurrence, batch);
  }

  @Transactional(readOnly = true)
  public List<SessionView> sessions(Jwt jwt, LocalDate date) {
    var actor = authorization.actor(jwt);
    Long academyId = actor.academyId();
    List<TrainingSession> all = sessions.findByAcademyIdAndSessionDateOrderByStartTime(academyId, date == null ? LocalDate.now() : date);
    return all.stream().filter(session -> actor.manager() || assignedCoach(actor, session.getBatchId()))
        .map(session -> sessionView(session, batch(session.getBatchId(), academyId))).toList();
  }

  private boolean assignedCoach(AcademyAuthorization.Actor actor, Long batchId) {
    if (actor.membership() == null || !actor.roles().contains("INSTRUCTOR")) return false;
    TrainingBatch batch = batch(batchId, actor.academyId());
    return batch.getCoachIds().contains(actor.membership().getId());
  }

  private EnrollmentView enrollmentView(AcademyEnrollment enrollment, AcademyStudent student, TrainingBatch batch) {
    return new EnrollmentView(enrollment.getId(), student.getId(), student.getStudentCode(),
        student.getFirstName() + " " + student.getLastName(), batch.getId(), batch.getName(),
        enrollment.getStartDate(), enrollment.getEndDate(), enrollment.getStatus());
  }

  private SessionView sessionView(TrainingSession session, TrainingBatch batch) {
    return new SessionView(session.getId(), batch.getId(), batch.getName(), batch.getSportId(),
        session.getSessionDate(), session.getStartTime(), session.getEndTime(), session.getStatus());
  }

  private AcademyStudent student(Long id, Long academyId) {
    return students.findByIdAndAcademyId(id, academyId).orElseThrow(() -> new ArenaOpsException(ErrorCode.RESOURCE_NOT_FOUND));
  }

  private TrainingBatch batch(Long id, Long academyId) {
    return batches.findById(id).filter(value -> academyId.equals(value.getAcademyId()))
        .orElseThrow(() -> new ArenaOpsException(ErrorCode.RESOURCE_NOT_FOUND));
  }
}