package com.arena.core.students;

import static com.arena.core.students.StudentContracts.*;

import com.arena.core.exception.ArenaOpsException;
import com.arena.core.exception.ErrorCode;
import com.arena.core.training.AcademyAuthorization;
import com.arena.core.training.AcademySport;
import com.arena.core.training.AcademySportRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AcademyStudentService {
  private final AcademyAuthorization authorization;
  private final AcademyStudentRepository students;
  private final AcademyStudentSportRepository studentSports;
  private final AcademySportRepository sports;

  public AcademyStudentService(
      AcademyAuthorization authorization,
      AcademyStudentRepository students,
      AcademyStudentSportRepository studentSports,
      AcademySportRepository sports) {
    this.authorization = authorization;
    this.students = students;
    this.studentSports = studentSports;
    this.sports = sports;
  }

  public Page<Response> list(Jwt jwt, String search, String status, int page, int size) {
    Long academyId = authorization.manage(jwt).academyId();
    if (page < 0 || size < 1 || size > 100) throw new ArenaOpsException(ErrorCode.INVALID_REQUEST);
    if (status != null && !Set.of("ACTIVE", "INACTIVE").contains(status.toUpperCase(Locale.ROOT))) {
      throw new ArenaOpsException(ErrorCode.INVALID_REQUEST);
    }
    Specification<AcademyStudent> scope = (root, query, cb) -> cb.equal(root.get("academyId"), academyId);
    if (status != null && !status.isBlank()) {
      String normalizedStatus = status.toUpperCase(Locale.ROOT);
      scope = scope.and((root, query, cb) -> cb.equal(root.get("status"), normalizedStatus));
    }
    if (search != null && !search.isBlank()) {
      String term = "%" + escape(search.strip().toLowerCase(Locale.ROOT)) + "%";
      scope = scope.and((root, query, cb) -> cb.or(
          cb.like(cb.lower(root.get("studentCode")), term, '\\'),
          cb.like(cb.lower(root.get("firstName")), term, '\\'),
          cb.like(cb.lower(root.get("lastName")), term, '\\'),
          cb.like(cb.lower(root.get("email")), term, '\\'),
          cb.like(cb.lower(root.get("phone")), term, '\\'),
          cb.like(cb.lower(root.get("guardianName")), term, '\\')));
    }
    Page<AcademyStudent> results = students.findAll(scope,
        PageRequest.of(page, size, Sort.by("lastName").and(Sort.by("firstName")).and(Sort.by("id"))));
    return results.map(this::response);
  }

  public Response get(Jwt jwt, Long id) {
    Long academyId = authorization.manage(jwt).academyId();
    return response(student(id, academyId));
  }

  public Response create(Jwt jwt, Request request) {
    var actor = authorization.manage(jwt);
    validateSports(request.sportIds(), actor.academyId());
    AcademyStudent student = new AcademyStudent();
    student.setAcademyId(actor.academyId());
    apply(student, request, "ACTIVE");
    student.setStudentCode("TMP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
    student.setCreatedBy(actor.user().getUsername());
    student.setUpdatedBy(actor.user().getUsername());
    students.saveAndFlush(student);
    student.setStudentCode("STU-%06d".formatted(student.getId()));
    studentSports.saveAll(links(request.sportIds(), student));
    return response(students.save(student));
  }

  public Response update(Jwt jwt, Long id, Request request) {
    var actor = authorization.manage(jwt);
    AcademyStudent student = student(id, actor.academyId());
    if (request.version() == null || !request.version().equals(student.getVersion())) {
      throw new ArenaOpsException(ErrorCode.CONFLICT);
    }
    validateSports(request.sportIds(), actor.academyId());
    apply(student, request, student.getStatus());
    student.setUpdatedBy(actor.user().getUsername());
    studentSports.deleteAllByStudentIdAndAcademyId(id, actor.academyId());
    studentSports.saveAll(links(request.sportIds(), student));
    return response(students.save(student));
  }

  private void apply(AcademyStudent student, Request request, String currentStatus) {
    student.setFirstName(request.firstName().strip());
    student.setLastName(request.lastName().strip());
    student.setDateOfBirth(request.dateOfBirth());
    student.setGender(clean(request.gender()));
    student.setEmail(clean(request.email()));
    student.setPhone(clean(request.phone()));
    student.setGuardianName(clean(request.guardianName()));
    student.setGuardianPhone(clean(request.guardianPhone()));
    student.setGuardianEmail(clean(request.guardianEmail()));
    student.setAddress(clean(request.address()));
    student.setEmergencyContact(clean(request.emergencyContact()));
    student.setNotes(clean(request.notes()));
    String status = request.status() == null || request.status().isBlank() ? currentStatus : request.status().toUpperCase(Locale.ROOT);
    if (!Set.of("ACTIVE", "INACTIVE").contains(status)) throw new ArenaOpsException(ErrorCode.INVALID_REQUEST);
    student.setStatus(status);
  }

  private void validateSports(List<Long> sportIds, Long academyId) {
    if (sportIds == null || sportIds.isEmpty()) return;
    Set<Long> unique = new HashSet<>(sportIds);
    if (unique.size() != sportIds.size()) throw new ArenaOpsException(ErrorCode.INVALID_REQUEST);
    for (Long id : unique) {
      AcademySport sport = sports.findById(id).orElseThrow(() -> new ArenaOpsException(ErrorCode.RESOURCE_NOT_FOUND));
      if (!academyId.equals(sport.getAcademyId()) || !sport.isActive()) {
        throw new ArenaOpsException(ErrorCode.RESOURCE_NOT_FOUND);
      }
    }
  }

  private List<AcademyStudentSport> links(List<Long> sportIds, AcademyStudent student) {
    if (sportIds == null) return List.of();
    return sportIds.stream().map(id -> new AcademyStudentSport(student.getAcademyId(), student.getId(), id)).toList();
  }

  private Response response(AcademyStudent student) {
    List<Long> sportIds = studentSports.findByStudentIdAndAcademyIdOrderBySportId(student.getId(), student.getAcademyId())
        .stream().map(AcademyStudentSport::getSportId).toList();
    return new Response(student.getId(), student.getStudentCode(), student.getFirstName(), student.getLastName(),
        student.getDateOfBirth(), student.getGender(), student.getEmail(), student.getPhone(), student.getGuardianName(),
        student.getGuardianPhone(), student.getGuardianEmail(), student.getAddress(), student.getEmergencyContact(),
        student.getNotes(), student.getStatus(), sportIds, student.getCreatedAt(), student.getVersion());
  }

  private AcademyStudent student(Long id, Long academyId) {
    return students.findByIdAndAcademyId(id, academyId)
        .orElseThrow(() -> new ArenaOpsException(ErrorCode.RESOURCE_NOT_FOUND));
  }

  private String clean(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }

  private String escape(String value) {
    return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }
}