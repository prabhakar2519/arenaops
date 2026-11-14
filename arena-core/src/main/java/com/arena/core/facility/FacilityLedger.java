package com.arena.core.facility;
import jakarta.persistence.*;
import org.springframework.stereotype.Repository;
import java.util.*;
/** Caller holds the tenant row lock; counter locks are additionally taken in sorted type order. */
@Repository
public class FacilityLedger {
 @PersistenceContext private EntityManager em;
 public record Replay(String fingerprint,String response){}
 public Optional<Replay> replay(Long academy,UUID key){var rows=em.createNativeQuery("select fingerprint,response from {h-schema}facility_operation where academy_id=:a and operation='CREATE' and request_key=:k").setParameter("a",academy).setParameter("k",key.toString()).getResultList();if(rows.isEmpty())return Optional.empty();Object[] row=(Object[])rows.get(0);return Optional.of(new Replay((String)row[0],(String)row[1]));}
 public void remember(Long academy,Long sport,UUID key,String fingerprint,String response){em.createNativeQuery("insert into {h-schema}facility_operation(academy_id,sport_id,operation,request_key,fingerprint,response,created_at) values(:a,:s,'CREATE',:k,:f,:r,CURRENT_TIMESTAMP)").setParameter("a",academy).setParameter("s",sport).setParameter("k",key.toString()).setParameter("f",fingerprint).setParameter("r",response).executeUpdate();}
 public long allocate(Long academy,Long sport,String type,String prefix,int count){
  em.createNativeQuery("insert into {h-schema}facility_code_counter(academy_id,sport_id,facility_type,last_number) values(:a,:s,:t,0) on conflict do nothing").setParameter("a",academy).setParameter("s",sport).setParameter("t",type).executeUpdate();
  long last=((Number)em.createNativeQuery("select last_number from {h-schema}facility_code_counter where academy_id=:a and sport_id=:s and facility_type=:t for update").setParameter("a",academy).setParameter("s",sport).setParameter("t",type).getSingleResult()).longValue();
  // Preserve the stronger existing tenant-wide code namespace, including legacy/inactive rows.
  java.math.BigDecimal existing=new java.math.BigDecimal(((Number)em.createNativeQuery("select coalesce(max(cast(substring(code from :offset) as numeric)),0) from {h-schema}academy_play_area where academy_id=:a and code ~ :pattern").setParameter("a",academy).setParameter("offset",prefix.length()+1).setParameter("pattern","^"+prefix+"[0-9]+$").getSingleResult()).toString());
  long first,end;try{first=Math.addExact(Math.max(last,existing.longValueExact()),1L);end=Math.addExact(first,count-1L);}catch(ArithmeticException ex){throw new FacilityFailure(com.arena.core.exception.ErrorCode.FACILITY_INVALID_REQUEST,"FACILITY_CODE_SPACE_EXHAUSTED");}
  em.createNativeQuery("update {h-schema}facility_code_counter set last_number=:n where academy_id=:a and sport_id=:s and facility_type=:t").setParameter("n",end).setParameter("a",academy).setParameter("s",sport).setParameter("t",type).executeUpdate();return first;
 }
}
