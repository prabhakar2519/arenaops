package com.arena.core.training;

import static com.arena.core.training.TrainingOperationsContracts.Enroll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.arena.core.entity.AppUserEntity;
import com.arena.core.exception.ArenaOpsException;
import com.arena.core.exception.ErrorCode;
import com.arena.core.students.AcademyEnrollmentRepository;
import com.arena.core.students.AcademyStudent;
import com.arena.core.students.AcademyStudentRepository;
import com.arena.core.students.AcademyStudentSport;
import com.arena.core.students.AcademyStudentSportRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class TrainingOperationsServiceTest {
  @Mock private AcademyAuthorization authorization;
  @Mock private AcademyStudentRepository students;
  @Mock private AcademyStudentSportRepository studentSports;
  @Mock private AcademyEnrollmentRepository enrollments;
  @Mock private TrainingBatchRepository batches;
  @Mock private BatchScheduleRepository schedules;
  @Mock private TrainingSessionRepository sessions;
  private TrainingOperationsService service;
  private Jwt jwt;
  private TrainingBatch batch;

  @BeforeEach
  void setUp() {
    service = new TrainingOperationsService(authorization, students, studentSports, enrollments, batches, schedules, sessions);
    jwt = Jwt.withTokenValue("token").header("alg", "none").subject("owner").build();
    AppUserEntity owner = AppUserEntity.builder().id(1L).username("owner").customerId(12L).build();
    when(authorization.manage(jwt)).thenReturn(new AcademyAuthorization.Actor(owner, null, Set.of("OWNER")));
    AcademyStudent student = new AcademyStudent(); student.setId(44L); student.setAcademyId(12L); student.setStatus("ACTIVE");
    when(students.findByIdAndAcademyId(44L, 12L)).thenReturn(Optional.of(student));
    batch = new TrainingBatch(); batch.setId(9L); batch.setAcademyId(12L); batch.setSportId(5L); batch.setStatus("ACTIVE");
    batch.setCapacity(1); batch.setName("Beginners");
    when(batches.findByIdAndAcademyIdForUpdate(9L, 12L)).thenReturn(Optional.of(batch));
    when(studentSports.findByStudentIdAndAcademyIdOrderBySportId(44L, 12L)).thenReturn(List.of(new AcademyStudentSport(12L, 44L, 5L)));
  }

  @Test
  void duplicateActiveEnrollmentIsRejected() {
    when(enrollments.existsByAcademyIdAndStudentIdAndBatchIdAndStatus(12L, 44L, 9L, "ACTIVE")).thenReturn(true);

    ArenaOpsException error = assertThrows(ArenaOpsException.class, () -> service.enroll(jwt,
        new Enroll(44L, 9L, LocalDate.now(), null)));

    assertEquals(ErrorCode.CONFLICT, error.getErrorCode());
    verify(enrollments, never()).saveAndFlush(any());
  }

  @Test
  void fullBatchRejectsAnAdditionalEnrollment() {
    when(enrollments.existsByAcademyIdAndStudentIdAndBatchIdAndStatus(12L, 44L, 9L, "ACTIVE")).thenReturn(false);
    when(enrollments.countByAcademyIdAndBatchIdAndStatus(12L, 9L, "ACTIVE")).thenReturn(1L);

    ArenaOpsException error = assertThrows(ArenaOpsException.class, () -> service.enroll(jwt,
        new Enroll(44L, 9L, LocalDate.now(), null)));

    assertEquals(ErrorCode.CONFLICT, error.getErrorCode());
    verify(enrollments, never()).saveAndFlush(any());
  }
}