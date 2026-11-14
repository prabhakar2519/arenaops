package com.arena.core.training;
import com.arena.core.exception.*;
import com.arena.core.service.AuditService;
import com.arena.core.enums.AuditActorType;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.oauth2.jwt.Jwt;
import java.util.*;
@Service @RequiredArgsConstructor @Transactional
public class AcademyFacilityService {
 private final AcademyAuthorization authorization;
 private final AcademySetupService setup;
 private final AcademySportRepository sports;
 private final AcademyPlayAreaRepository areas;
 private final AuditService audit;
 public record Area(Long version,@NotNull Long sportId,@NotBlank @Size(max=160) String name,@NotBlank @Pattern(regexp="[A-Za-z0-9_-]+") @Size(max=64) String code,@NotBlank @Pattern(regexp="COURT|TABLE|FIELD|OTHER") String kind,@Positive Integer capacity,boolean active){}
 public List<AcademyPlayArea> list(Jwt jwt){var a=authorization.manage(jwt);return areas.findByAcademyIdOrderByName(a.academyId());}
 public AcademyPlayArea save(Jwt jwt,Long id,Area r){var actor=authorization.manage(jwt);var c=setup.academy(actor.academyId());var sport=sports.findById(r.sportId()).filter(s->Objects.equals(s.getAcademyId(),c.getId())&&s.isActive()&&s.isSessions()).orElseThrow(()->new ArenaOpsException(ErrorCode.INVALID_REQUEST));
  var f=id==null?new AcademyPlayArea():areas.findById(id).filter(v->Objects.equals(v.getAcademyId(),c.getId())).orElseThrow(()->new ArenaOpsException(ErrorCode.RESOURCE_NOT_FOUND));
  if(id!=null && !sports.findById(f.getSportId()).filter(s->Objects.equals(s.getAcademyId(),c.getId())&&s.isActive()).isPresent())throw new ArenaOpsException(ErrorCode.FACILITY_CAPABILITY_DISABLED);
  if(f.getFacilityType()!=null)throw new ArenaOpsException(ErrorCode.INVALID_REQUEST);
  if(id!=null&&!Objects.equals(f.getVersion(),r.version()))throw new ArenaOpsException(ErrorCode.CONFLICT);
  String code=r.code().trim().toUpperCase(Locale.ROOT);
  boolean protectedSport=com.arena.core.facility.FacilityService.supported(sport)||(id!=null&&sports.findById(f.getSportId()).map(com.arena.core.facility.FacilityService::supported).orElse(false));
  if(protectedSport&&(id==null||!Objects.equals(f.getCode(),code)||!Objects.equals(f.getSportId(),sport.getId())))throw new com.arena.core.facility.FacilityFailure(ErrorCode.FACILITY_INVALID_REQUEST,"USE_FACILITY_BULK_API",Map.of("code","Generate new facilities through Facilities & Resources; existing codes and sport associations are immutable."),List.of());
  if(areas.findByAcademyIdOrderByName(c.getId()).stream().anyMatch(v->!Objects.equals(v.getId(),id)&&code.equals(v.getCode())))throw new ArenaOpsException(ErrorCode.CONFLICT);
  f.setAcademyId(c.getId());f.setSportId(sport.getId());f.setName(r.name().trim());f.setCode(code);f.setKind(r.kind());f.setCapacity(r.capacity());f.setActive(r.active());if(id==null)f.setCreatedBy(jwt.getSubject());f.setUpdatedBy(jwt.getSubject());areas.saveAndFlush(f);audit.record("ACADEMY_FACILITY_SAVED",c.getId(),AuditActorType.CUSTOMER,jwt.getSubject(),null,null,null,"recordId="+f.getId());setup.evaluate(c);return f;
 }
}
