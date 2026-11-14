package com.arena.core.profile;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonAnySetter;
@io.swagger.v3.oas.annotations.media.Schema(additionalProperties=io.swagger.v3.oas.annotations.media.Schema.AdditionalPropertiesValue.FALSE)
public record ProfileUpdate(
 @NotNull Long version,
 @NotBlank @Size(min=2,max=120) String displayName,@io.swagger.v3.oas.annotations.media.Schema(types={"string","null"}) @Pattern(regexp="SPORTS_ACADEMY|SPORTS_CLUB|SPORTS_RECREATION_CENTRE|GYM_FITNESS_CENTRE|OTHER") String businessType,@io.swagger.v3.oas.annotations.media.Schema(types={"string","null"}) @Email @Size(max=255) String publicContactEmail,@io.swagger.v3.oas.annotations.media.Schema(types={"string","null"}) @Size(max=50) @ContactPhone String publicPhone,@io.swagger.v3.oas.annotations.media.Schema(types={"string","null"}) @Size(max=255) String addressLine,@io.swagger.v3.oas.annotations.media.Schema(types={"string","null"}) @Size(max=100) String city,@io.swagger.v3.oas.annotations.media.Schema(types={"string","null"}) @Size(max=100) String region,@io.swagger.v3.oas.annotations.media.Schema(types={"string","null"}) @Size(max=30) String postalCode,@io.swagger.v3.oas.annotations.media.Schema(types={"string","null"}) @Size(max=100) String country,
 @NotNull @Pattern(regexp="KEEP|REPLACE|REMOVE") String logoAction) {
 public ProfileUpdate {displayName=trim(displayName);businessType=trim(businessType);publicContactEmail=trim(publicContactEmail);publicPhone=trim(publicPhone);addressLine=trim(addressLine);city=trim(city);region=trim(region);postalCode=trim(postalCode);country=trim(country);}
 private static String trim(String v){return v==null?null:v.trim();}
 @JsonAnySetter public void rejectUnknown(String name,Object value){throw new IllegalArgumentException("Unknown profile field");}
}
