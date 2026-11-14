package com.arena.core.training;

import static com.arena.core.training.AttendanceContracts.*;

import com.arena.core.exception.ArenaOpsException;
import com.arena.core.exception.ErrorCode;
import com.arena.core.students.AcademyEnrollment;
import com.arena.core.students.AcademyEnrollmentRepository;
import com.arena.core.students.AcademyStudent;
import com.arena.core.students.AcademyStudentRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional
public class AttendanceService {
  private final AcademyAuthorization authorization;
  private final AcademyStudentRepository students;
  private final AcademyEnrollmentRepository enrollments;
  private final TrainingBatchRepository batches;
  private final TrainingSessionRepository sessions;
  private final AttendanceCredentialRepository credentials;
  private final AcademyAttendanceRepository attendance;

  public AttendanceService(AcademyAuthorization authorization, AcademyStudentRepository students,
      AcademyEnrollmentRepository enrollments, TrainingBatchRepository batches, TrainingSessionRepository sessions,
      AttendanceCredentialRepository credentials, AcademyAttendanceRepository attendance) {
    this.authorization = authorization; this.students = students; this.enrollments = enrollments;
    this.batches = batches; this.sessions = sessions; this.credentials = credentials; this.attendance = attendance;
  }

  public Credential issueQr(Jwt jwt, Long studentId) { return issue(jwt, studentId, "QR", null); }
  public Credential registerNfc(Jwt jwt, Long studentId, String tagId) { return issue(jwt, studentId, "NFC", tagId); }

  private Credential issue(Jwt jwt, Long studentId, String kind, String supplied) {
    var actor = authorization.manage(jwt);
    AcademyStudent student = student(studentId, actor.academyId());
    String raw = kind.equals("QR") ? "AOQR-" + UUID.randomUUID() : supplied == null ? "" : supplied.strip();
    if (raw.isBlank() || raw.length() > 200) throw new ArenaOpsException(ErrorCode.INVALID_REQUEST);
    credentials.findByStudentIdAndAcademyIdAndKindAndActiveTrue(studentId, actor.academyId(), kind).forEach(existing -> {
      existing.setActive(false); existing.setRevokedAt(LocalDateTime.now());
    });
    AttendanceCredential credential = new AttendanceCredential();
    credential.setAcademyId(actor.academyId()); credential.setStudentId(studentId); credential.setKind(kind);
    credential.setTokenHash(hash(raw)); credential.setCreatedBy(actor.user().getUsername());
    credential = credentials.saveAndFlush(credential);
    return new Credential(credential.getId(), kind, kind.equals("QR") ? raw : null,
        kind.equals("QR") ? "Store this QR credential securely; it is shown only once." : "Tag registered.");
  }

  public void revoke(Jwt jwt, Long credentialId) {
    var actor = authorization.manage(jwt);
    AttendanceCredential credential = credentials.findById(credentialId)
        .filter(value -> actor.academyId().equals(value.getAcademyId()))
        .orElseThrow(() -> new ArenaOpsException(ErrorCode.RESOURCE_NOT_FOUND));
    credential.setActive(false); credential.setRevokedAt(LocalDateTime.now());
  }

  public AttendanceView captureQr(Jwt jwt, Capture request) { return capture(jwt, request, "QR"); }
  public AttendanceView captureNfc(Jwt jwt, Capture request) { return capture(jwt, request, "NFC"); }

  private AttendanceView capture(Jwt jwt, Capture request, String method) {
    var actor = authorization.actor(jwt);
    TrainingSession session = session(request.sessionId(), actor.academyId());
    requireSessionAccess(actor, session);
    AttendanceCredential credential = credentials.findByTokenHashAndActiveTrue(hash(request.credential().strip()))
        .filter(value -> method.equals(value.getKind()))
        .orElseThrow(() -> new ArenaOpsException(ErrorCode.ACCESS_DENIED));
    if (!actor.academyId().equals(credential.getAcademyId())) throw new ArenaOpsException(ErrorCode.ACCESS_DENIED);
    AcademyStudent student = student(credential.getStudentId(), actor.academyId());
    AcademyEnrollment enrollment = activeEnrollment(student.getId(), session);
    return record(actor.user().getUsername(), student, enrollment, session, method, "PROVISIONAL", null, null);
  }

  public AttendanceView manual(Jwt jwt, Manual request) {
    var actor = authorization.actor(jwt);
    TrainingSession session = session(request.sessionId(), actor.academyId());
    requireSessionAccess(actor, session);
    AcademyStudent student = student(request.studentId(), actor.academyId());
    AcademyEnrollment enrollment = activeEnrollment(student.getId(), session);
    String state = normalizeFinalStatus(request.status());
    return record(actor.user().getUsername(), student, enrollment, session, "MANUAL", state, state, request.note());
  }

  public AttendanceView confirm(Jwt jwt, Long attendanceId, Confirm request) {
    var actor = authorization.actor(jwt);
    AcademyAttendance record = attendance.findByIdAndAcademyId(attendanceId, actor.academyId())
        .orElseThrow(() -> new ArenaOpsException(ErrorCode.RESOURCE_NOT_FOUND));
    requireSessionAccess(actor, session(record.getSessionId(), actor.academyId()));
    if (!"PROVISIONAL".equals(record.getStatus())) throw new ArenaOpsException(ErrorCode.INVALID_STATE);
    record.setStatus(normalizeFinalStatus(request.status()));
    record.setNote(clean(request.note()));
    record.setConfirmedAt(LocalDateTime.now());
    record.setConfirmedBy(actor.user().getUsername());
    return view(attendance.save(record));
  }

  @Transactional(readOnly = true)
  public List<AttendanceView> sessionAttendance(Jwt jwt, Long sessionId) {
    var actor = authorization.actor(jwt);
    TrainingSession session = session(sessionId, actor.academyId());
    requireSessionAccess(actor, session);
    return attendance.findByAcademyIdAndSessionIdOrderByCapturedAt(actor.academyId(), sessionId).stream().map(this::view).toList();
  }

  @Transactional(readOnly = true)
  public List<SessionStudent> sessionStudents(Jwt jwt, Long sessionId) {
    var actor = authorization.actor(jwt);
    TrainingSession session = session(sessionId, actor.academyId());
    requireSessionAccess(actor, session);
    return enrollments.findByAcademyIdAndBatchIdOrderByCreatedAtDesc(actor.academyId(), session.getBatchId()).stream()
        .filter(enrollment -> "ACTIVE".equals(enrollment.getStatus()))
        .filter(enrollment -> !session.getSessionDate().isBefore(enrollment.getStartDate()))
        .filter(enrollment -> enrollment.getEndDate() == null || !session.getSessionDate().isAfter(enrollment.getEndDate()))
        .map(enrollment -> {
          AcademyStudent student = student(enrollment.getStudentId(), actor.academyId());
          AcademyAttendance record = attendance.findBySessionIdAndEnrollmentId(sessionId, enrollment.getId()).orElse(null);
          return new SessionStudent(student.getId(), student.getStudentCode(), student.getFirstName(), student.getLastName(),
              record == null ? null : record.getId(), record == null ? "NOT_RECORDED" : record.getStatus());
        }).toList();
  }

  @Transactional(readOnly = true)
  public List<AttendanceView> studentAttendance(Jwt jwt, Long studentId) {
    Long academyId = authorization.manage(jwt).academyId();
    student(studentId, academyId);
    return attendance.findByAcademyIdAndStudentIdOrderByCapturedAtDesc(academyId, studentId).stream().map(this::view).toList();
  }

  private AttendanceView record(String username, AcademyStudent student, AcademyEnrollment enrollment,
      TrainingSession session, String method, String initialStatus, String finalStatus, String note) {
    var existing = attendance.findBySessionIdAndEnrollmentId(session.getId(), enrollment.getId());
    if (existing.isPresent()) return view(existing.get());
    AcademyAttendance record = new AcademyAttendance();
    record.setAcademyId(session.getAcademyId()); record.setSessionId(session.getId());
    record.setEnrollmentId(enrollment.getId()); record.setStudentId(student.getId());
    record.setCaptureMethod(method); record.setStatus(finalStatus == null ? initialStatus : finalStatus);
    record.setCapturedBy(username); record.setNote(clean(note));
    if (finalStatus != null) { record.setConfirmedAt(LocalDateTime.now()); record.setConfirmedBy(username); }
    return view(attendance.saveAndFlush(record));
  }

  private AcademyEnrollment activeEnrollment(Long studentId, TrainingSession session) {
    AcademyEnrollment enrollment = enrollments.findByAcademyIdAndStudentIdAndBatchIdAndStatus(
        session.getAcademyId(), studentId, session.getBatchId(), "ACTIVE")
        .orElseThrow(() -> new ArenaOpsException(ErrorCode.ACCESS_DENIED));
    if (session.getSessionDate().isBefore(enrollment.getStartDate()) || enrollment.getEndDate() != null && session.getSessionDate().isAfter(enrollment.getEndDate())) {
      throw new ArenaOpsException(ErrorCode.ACCESS_DENIED);
    }
    return enrollment;
  }

  private void requireSessionAccess(AcademyAuthorization.Actor actor, TrainingSession session) {
    if (actor.manager()) return;
    if (actor.membership() == null || !actor.roles().contains("INSTRUCTOR")
        || !batches.findById(session.getBatchId()).filter(batch -> actor.academyId().equals(batch.getAcademyId()))
            .map(batch -> batch.getCoachIds().contains(actor.membership().getId())).orElse(false)) {
      throw new ArenaOpsException(ErrorCode.ACCESS_DENIED);
    }
  }

  private String normalizeFinalStatus(String status) {
    String value = status == null ? "" : status.toUpperCase(Locale.ROOT);
    if (!List.of("PRESENT", "ABSENT", "LATE").contains(value)) throw new ArenaOpsException(ErrorCode.INVALID_REQUEST);
    return value;
  }

  private AcademyStudent student(Long id, Long academyId) {
    return students.findByIdAndAcademyId(id, academyId).orElseThrow(() -> new ArenaOpsException(ErrorCode.RESOURCE_NOT_FOUND));
  }

  private TrainingSession session(Long id, Long academyId) {
    return sessions.findByIdAndAcademyId(id, academyId).orElseThrow(() -> new ArenaOpsException(ErrorCode.RESOURCE_NOT_FOUND));
  }

  private AttendanceView view(AcademyAttendance record) {
    AcademyStudent student = student(record.getStudentId(), record.getAcademyId());
    return new AttendanceView(record.getId(), student.getId(), student.getStudentCode(),
        student.getFirstName() + " " + student.getLastName(), record.getSessionId(), record.getStatus(),
        record.getCaptureMethod(), record.getCapturedAt(), record.getConfirmedAt(), record.getNote());
  }

  private String clean(String value) { return value == null || value.isBlank() ? null : value.strip(); }
  private String hash(String raw) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8))); }
    catch (Exception error) { throw new IllegalStateException("Unable to process attendance credential"); }
  }
}