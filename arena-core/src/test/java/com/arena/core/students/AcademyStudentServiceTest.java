package com.arena.core.students;

import static com.arena.core.students.StudentContracts.Request;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.arena.core.entity.AppUserEntity;
import com.arena.core.exception.ArenaOpsException;
import com.arena.core.exception.ErrorCode;
import com.arena.core.training.AcademyAuthorization;
import com.arena.core.training.AcademySport;
import com.arena.core.training.AcademySportRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class AcademyStudentServiceTest {
  @Mock private AcademyAuthorization authorization;
  @Mock private AcademyStudentRepository students;
  @Mock private AcademyStudentSportRepository studentSports;
  @Mock private AcademySportRepository sports;

  private AcademyStudentService service;
  private Jwt jwt;

  @BeforeEach
  void setUp() {
    service = new AcademyStudentService(authorization, students, studentSports, sports);
    jwt = Jwt.withTokenValue("token").header("alg", "none").subject("owner").build();
    var user = AppUserEntity.builder().id(7L).username("owner").customerId(12L).build();
    when(authorization.manage(jwt)).thenReturn(new AcademyAuthorization.Actor(user, null, java.util.Set.of("OWNER")));
  }

  @Test
  void minorMayBeRegisteredWithoutOwnContactDetails() {
    AcademySport sport = new AcademySport();
    sport.setId(5L);
    sport.setAcademyId(12L);
    sport.setActive(true);
    when(sports.findById(5L)).thenReturn(Optional.of(sport));
    when(students.saveAndFlush(any())).thenAnswer(invocation -> {
      AcademyStudent saved = invocation.getArgument(0);
      saved.setId(41L);
      saved.setCreatedAt(java.time.LocalDateTime.now());
      return saved;
    });
    when(students.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(studentSports.findByStudentIdAndAcademyIdOrderBySportId(41L, 12L))
        .thenReturn(List.of(new AcademyStudentSport(12L, 41L, 5L)));

    var result = service.create(jwt, new Request("Asha", "Rao", LocalDate.parse("2015-03-10"), null,
        null, null, "Parent", "5550100", null, null, null, null, List.of(5L), null, null));

    assertEquals("STU-000041", result.studentCode());
    assertNull(result.email());
    assertNull(result.phone());
    assertEquals(List.of(5L), result.sportIds());
    ArgumentCaptor<AcademyStudent> saved = ArgumentCaptor.forClass(AcademyStudent.class);
    verify(students).saveAndFlush(saved.capture());
    assertEquals(12L, saved.getValue().getAcademyId());
    verify(studentSports).saveAll(any());
  }

  @Test
  void crossAcademySportIsRejectedBeforeStudentIsSaved() {
    AcademySport sport = new AcademySport();
    sport.setId(5L);
    sport.setAcademyId(99L);
    sport.setActive(true);
    when(sports.findById(5L)).thenReturn(Optional.of(sport));

    ArenaOpsException error = assertThrows(ArenaOpsException.class, () -> service.create(jwt,
        new Request("Asha", "Rao", null, null, null, null, null, null, null, null, null, null,
            List.of(5L), null, null)));

    assertEquals(ErrorCode.RESOURCE_NOT_FOUND, error.getErrorCode());
    verify(students, never()).saveAndFlush(any());
  }
}