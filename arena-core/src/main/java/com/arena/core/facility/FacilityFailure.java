package com.arena.core.facility;
import com.arena.core.exception.*;
import java.util.*;
import lombok.Getter;
@Getter
public class FacilityFailure extends ArenaOpsException {
 private final String reasonCode;private final Map<String,String> fields;private final List<Map<String,Object>> conflicts;
 public FacilityFailure(ErrorCode code,String reason){this(code,reason,Map.of(),List.of());}
 public FacilityFailure(ErrorCode code,String reason,Map<String,String> fields,List<Map<String,Object>> conflicts){super(code);this.reasonCode=reason;this.fields=Map.copyOf(fields);this.conflicts=List.copyOf(conflicts);}
}
