package com.arena.core.training;
import com.arena.core.exception.*;
import java.util.Map;
public class ActivitySelectionException extends ArenaOpsException {
 private final Map<String,String> fields;
 public ActivitySelectionException(Map<String,String> fields){super(ErrorCode.VALIDATION_FAILED);this.fields=Map.copyOf(fields);}
 public Map<String,String> fields(){return fields;}
}
