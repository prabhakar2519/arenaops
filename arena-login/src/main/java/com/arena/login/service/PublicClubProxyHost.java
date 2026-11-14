package com.arena.login.service;
import jakarta.servlet.http.HttpServletRequest;import org.springframework.http.HttpHeaders;import org.springframework.beans.factory.annotation.Value;import org.springframework.stereotype.Component;
import java.util.*;import java.nio.charset.StandardCharsets;import javax.crypto.Mac;import javax.crypto.spec.SecretKeySpec;import com.arena.login.exception.*;
@Component
public class PublicClubProxyHost {
 private final String key,domain;
 @Value("${ARENA_ENV:unknown}") private String environment;
 @Value("${PUBLIC_CLUB_PREVIEW_ENABLED:false}") private boolean previewEnabled;
 public PublicClubProxyHost(@Value("${arena.public.host-key:}")String key,@Value("${arena.public.domain:arenaops.in}")String domain){this.key=key;this.domain=domain;}
 public void sign(HttpServletRequest request,HttpHeaders headers){String raw=request.getHeader("Host");if(raw==null||!raw.matches("[A-Za-z0-9.-]+(?::[0-9]{1,5})?"))throw new ArenaOpsException(ErrorCode.RESOURCE_NOT_FOUND);String host=raw.toLowerCase(Locale.ROOT).replaceFirst(":[0-9]+$","");String preview=request.getHeader("X-Arena-Preview-Slug");boolean localHostname=host.endsWith(".localhost");
  String checkinPath=request.getRequestURI().replaceFirst("^/api","");boolean checkin="POST".equals(request.getMethod())&&Set.of("/public/facility-check-ins/inspect","/public/facility-check-ins/submit").contains(checkinPath);
  boolean local=host.equals("localhost")||host.equals("127.0.0.1")||host.matches("192\\.168\\.[0-9]{1,3}\\.[0-9]{1,3}")||host.matches("10\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}")||host.matches("172\\.(1[6-9]|2[0-9]|3[01])\\.[0-9]{1,3}\\.[0-9]{1,3}");
  boolean shared=checkin&&preview==null&&!localHostname&&(host.equals(domain)||host.equals("www."+domain)||"sit".equals(environment)&&host.equals("sit."+domain)||"dev".equals(environment)&&(local||host.equals("dev."+domain)));
  if(shared)host=domain;
  if(preview!=null||localHostname){
   String path=request.getRequestURI().replaceFirst("^/api","");
   if(path.startsWith("/public/facility-check-ins/")&&!"POST".equals(request.getMethod()))throw new ArenaOpsException(ErrorCode.RESOURCE_NOT_FOUND);
   if((!previewEnabled&&!checkin)||!Set.of("dev","sit").contains(environment)||!Set.of("/public/club","/public/club/logo","/public/club/reservations","/public/facility-check-ins/inspect","/public/facility-check-ins/submit").contains(path))throw new ArenaOpsException(ErrorCode.RESOURCE_NOT_FOUND);
   boolean trusted="sit".equals(environment)?host.equals("sit."+domain):host.equals("localhost")||host.equals("127.0.0.1")||localHostname||host.matches("192\\.168\\.[0-9]{1,3}\\.[0-9]{1,3}")||host.matches("10\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}");
   String slug=localHostname?host.substring(0,host.length()-10):preview;
   if(!trusted||slug==null||!slug.matches("[a-z][a-z0-9-]{1,38}[a-z0-9]")||slug.contains("--")||preview!=null&&localHostname&&!slug.equals(preview))throw new ArenaOpsException(ErrorCode.RESOURCE_NOT_FOUND);
   host=slug+"."+domain;
  }
  String suffix="."+domain;if(key.length()<32||!shared&&!host.endsWith(suffix)||!shared&&!host.substring(0,host.length()-suffix.length()).matches("[a-z][a-z0-9-]{1,38}[a-z0-9]"))throw new ArenaOpsException(ErrorCode.RESOURCE_NOT_FOUND);try{String time=String.valueOf(java.time.Instant.now().getEpochSecond());var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));String signature=HexFormat.of().formatHex(mac.doFinal((host+"\n"+time).getBytes(StandardCharsets.UTF_8)));headers.set("X-Arena-Public-Host",host);headers.set("X-Arena-Public-Time",time);headers.set("X-Arena-Public-Signature",signature);}catch(Exception e){throw new ArenaOpsException(ErrorCode.RESOURCE_NOT_FOUND);}}
}
