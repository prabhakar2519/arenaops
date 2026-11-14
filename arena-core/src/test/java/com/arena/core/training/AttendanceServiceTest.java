package com.arena.core.training;

import static com.arena.core.training.AttendanceContracts.Capture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.arena.core.entity.AppUserEntity;
import com.arena.core.students.AcademyEnrollment;
import com.arena.core.students.AcademyEnrollmentRepository;
import com.arena.core.students.AcademyStudent;
import com.arena.core.students.AcademyStudentRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {
  @Mock private AcademyAuthorization authorization;
  @Mock private AcademyStudentRepository students;
  @Mock private AcademyEnrollmentRepository enrollments;
  @Mock private TrainingBatchRepository batches;
  @Mock private TrainingSessionRepository sessions;
  @Mock private AttendanceCredentialRepository credentials;
  @Mock private AcademyAttendanceRepository attendance;
  private AttendanceService service;
  private Jwt jwt;

  @BeforeEach
  void setUp() {
    service = new AttendanceService(authorization, students, enrollments, batches, sessions, credentials, attendance);
    jwt = Jwt.withTokenValue("token").header("alg", "none").subject("owner").build();
    AppUserEntity owner = AppUserEntity.builder().id(1L).username("owner").customerId(12L).build();
    when(authorization.actor(jwt)).thenReturn(new AcademyAuthorization.Actor(owner, null, Set.of("OWNER")));
    TrainingSession session = new TrainingSession(); session.setId(77L); session.setAcademyId(12L); session.setBatchId(9L);
    session.setSessionDate(LocalDate.now());
    when(sessions.findByIdAndAcademyId(77L, 12L)).thenReturn(Optional.of(session));
    AcademyStudent student = new AcademyStudent(); student.setId(44L); student.setAcademyId(12L); student.setStudentCode("STU-000044");
    student.setFirstName("Asha"); student.setLastName("Rao"); student.setStatus("ACTIVE");
    when(students.findByIdAndAcademyId(44L, 12L)).thenReturn(Optional.of(student));
    AcademyEnrollment enrollment = new AcademyEnrollment(); enrollment.setId(31L); enrollment.setAcademyId(12L);
    enrollment.setStudentId(44L); enrollment.setBatchId(9L); enrollment.setStartDate(LocalDate.now().minusDays(1)); enrollment.setStatus("ACTIVE");
    when(enrollments.findByAcademyIdAndStudentIdAndBatchIdAndStatus(12L, 44L, 9L, "ACTIVE")).thenReturn(Optional.of(enrollment));
  }

  @Test
  void qrCredentialCreatesProvisionalAttendanceForAnEnrolledStudent() throws Exception {
    String raw = "AOQR-test-secret";
    String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
    AttendanceCredential credential = new AttendanceCredential(); credential.setAcademyId(12L); credential.setStudentId(44L); credential.setKind("QR");
    when(credentials.findByTokenHashAndActiveTrue(hash)).thenReturn(Optional.of(credential));
    when(attendance.findBySessionIdAndEnrollmentId(77L, 31L)).thenReturn(Optional.empty());
    when(attendance.saveAndFlush(any())).thenAnswer(invocation -> { AcademyAttendance value = invocation.getArgument(0); value.setId(2L); value.setCapturedAt(java.time.LocalDateTime.now()); return value; });

    var result = service.captureQr(jwt, new Capture(raw, 77L));

    assertEquals("PROVISIONAL", result.status());
    assertEquals("QR", result.captureMethod());
    assertEquals("Asha Rao", result.studentName());
    verify(attendance).saveAndFlush(any(AcademyAttendance.class));
  }
}