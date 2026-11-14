package com.arena.core.documentation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.*;
/** Documentation projection of the existing strict JSON parser; never used to deserialize requests. */
public final class FacilityDocumentation {
 @Schema(name="FacilityQuantity",additionalProperties=Schema.AdditionalPropertiesValue.FALSE)
 public record Quantity(@NotNull @Schema(allowableValues={"ENGLISH","FRENCH","POOL","SEMI_ENGLISH","COURT"},example="COURT") String facilityType,
  @NotNull @Min(1) @Schema(description="Whole quantity <= context.limits.maxPerType (default 50)",example="2") Integer count){}
 @Schema(name="GenerateFacilities",additionalProperties=Schema.AdditionalPropertiesValue.FALSE)
 public record Generate(@NotEmpty @Size(max=5) List<@Valid Quantity> facilities){}
 @Schema(name="FacilityPatchItem",minProperties=3,additionalProperties=Schema.AdditionalPropertiesValue.FALSE,
  description="Omitted fields are unchanged. At least one editable field required. null clears name/rate; active never accepts null.")
 public record PatchItem(@NotNull @Min(1) Long facilityId,@NotNull @Min(0) Long version,
  @Size(max=160) @Schema(types={"string","null"}) String displayName,
  @Schema(types={"number","null"},exclusiveMinimumValue=0,maximum="9999999999.99",multipleOf=0.01,description="null clears pricing; numeric amount, not a string") @DecimalMin("0.01") BigDecimal hourlyRate,
  @Schema(types={"number","null"},exclusiveMinimumValue=0,maximum="9999999999.99",multipleOf=0.01,description="Tables only; courts require null/omission") @DecimalMin("0.01") BigDecimal frameRate,
  @Schema(description="Omit to preserve status; explicit null is invalid") Boolean active){}
 @Schema(name="PatchFacilities",additionalProperties=Schema.AdditionalPropertiesValue.FALSE)
 public record Patch(@NotEmpty @Schema(description="Distinct facility IDs; length <= context.limits.maxBulk (default 200)") List<@Valid PatchItem> facilities){}
 public record Conflict(Long facilityId,String code,Long submittedVersion,Long currentVersion){}
 @Schema(name="FacilityError")
 public record Failure(String timestamp,int status,String errorCode,String reasonCode,String description,String path,String correlationId,Map<String,String> fieldErrors,List<Conflict> conflicts){}
}
