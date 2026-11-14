package com.arena.core.documentation;
import com.arena.core.training.*;
import java.time.LocalDateTime;
import java.util.*;
/** Shapes for existing Map responses; records document wire data without changing controllers. */
public final class ResponseDocumentation {
 public record ServiceStatus(@io.swagger.v3.oas.annotations.media.Schema(example="arena-core") String service,@io.swagger.v3.oas.annotations.media.Schema(example="ready") String status){}
 public record Health(@io.swagger.v3.oas.annotations.media.Schema(example="UP") String status){}
 public record Limits(int maxPerType,int maxBulk){}
 public record FacilityContext(Long academyId,List<AcademySport> sports,Limits limits){}
 public record Membership(Set<String> roles,Long academyId,Long staffId){}
 public record Context(boolean eligible,String name,String timezone,Boolean skipped,Boolean setupComplete,Boolean ownerManaged,Long version){}
 public record SportsPage(List<AcademySport> content,long totalElements,int totalPages,int number,int size){}
 public record StaffPage(List<AcademyStaff> content,long totalElements,int totalPages,int number,int size){}
 public record BatchesPage(List<TrainingBatch> content,long totalElements,int totalPages,int number,int size){}
 public record CataloguePage(List<SportCatalogue> content,long totalElements,int totalPages,int number,int size){}
 public record BatchDetail(TrainingBatch batch,List<BatchSchedule> schedules){}
 public record SetupProfile(String name,String ownerName,String email,String phone,String timezone){}
 public record SetupProgress(long percentage,String status,List<AcademySetupService.Step> steps,Boolean ownerManaged,Boolean reviewConfirmed,LocalDateTime evaluatedAt,Long version,SetupProfile profile){}
 public record AccessCheck(boolean allowed,Boolean billingAllowed){}
 public record EmailCheck(boolean registered){}
}
