package com.arena.core.publicclub;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.security.MessageDigest;
import java.util.*;
import javax.crypto.Mac;import javax.crypto.spec.SecretKeySpec;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.arena.core.exception.*;
@Component
public class PublicClubHost {
 private final String key,domain,scheme,port;
 public PublicClubHost(@Value("${arena.public.host-key:}")String key,@Value("${arena.public.domain:arenaops.in}")String domain,@Value("${arena.public.scheme:https}")String scheme,@Value("${arena.public.port:}")String port){this.key=key;this.domain=domain;this.scheme=scheme;this.port=port;}
 public String verifiedSlug(HttpServletRequest request){try{return slug(verifiedHost(request));}catch(Exception e){throw new ArenaOpsException(ErrorCode.RESOURCE_NOT_FOUND);}}
 /** Only check-in controllers accept a signed shared host; club website routes still require a slug. */
 public String verifiedCheckinSlug(HttpServletRequest request){try{String host=verifiedHost(request);return host.equals(domain)?null:slug(host);}catch(Exception e){throw new ArenaOpsException(ErrorCode.RESOURCE_NOT_FOUND);}}
 private String verifiedHost(HttpServletRequest request){
  String host=request.getHeader("X-Arena-Public-Host"),time=request.getHeader("X-Arena-Public-Time"),signature=request.getHeader("X-Arena-Public-Signature");
  try{if(key.length()<32||host==null||time==null||signature==null||Math.abs(Instant.now().getEpochSecond()-Long.parseLong(time))>60||!MessageDigest.isEqual(sign(host+"\n"+time).getBytes(StandardCharsets.US_ASCII),signature.getBytes(StandardCharsets.US_ASCII)))throw new IllegalArgumentException();return host;}catch(Exception e){throw new ArenaOpsException(ErrorCode.RESOURCE_NOT_FOUND);}
 }
 public boolean signingConfigured(){return key.length()>=32;}
 public String slug(String host){String suffix="."+domain;String normalized=host.toLowerCase(Locale.ROOT);if(!normalized.equals(host)||!normalized.endsWith(suffix))throw new ArenaOpsException(ErrorCode.RESOURCE_NOT_FOUND);String s=normalized.substring(0,normalized.length()-suffix.length());return PublicClubService.normalizeSlug(s);}
 public String origin(String slug){return scheme+"://"+PublicClubService.normalizeSlug(slug)+"."+domain+(port.isBlank()?"":":"+port);}
 private String sign(String value)throws Exception{var m=Mac.getInstance("HmacSHA256");m.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));return HexFormat.of().formatHex(m.doFinal(value.getBytes(StandardCharsets.UTF_8)));}
}
