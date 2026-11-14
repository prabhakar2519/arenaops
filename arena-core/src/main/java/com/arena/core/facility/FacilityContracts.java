package com.arena.core.facility;
import com.arena.core.training.AcademyPlayArea;
import java.math.BigDecimal;
import java.util.*;
public final class FacilityContracts {
 private FacilityContracts(){}
 public record Quantity(String facilityType,Integer count){}
 public record Create(List<Quantity> facilities){}
 public record View(Long facilityId,Long registeredSportId,String code,String facilityType,String displayName,BigDecimal hourlyRate,BigDecimal frameRate,boolean active,boolean configured,Long version){}
 public record Result(List<View> facilities){}
 public static boolean configured(AcademyPlayArea f){return f.getFacilityType()!=null&&f.getHourlyRate()!=null&&f.getHourlyRate().signum()>0;}
 public static View view(AcademyPlayArea f){return new View(f.getId(),f.getSportId(),f.getCode(),f.getFacilityType(),f.getFacilityType()==null&&f.getDisplayName()==null?f.getName():f.getDisplayName(),f.getHourlyRate(),f.getFrameRate(),f.isActive(),configured(f),f.getVersion());}
}
