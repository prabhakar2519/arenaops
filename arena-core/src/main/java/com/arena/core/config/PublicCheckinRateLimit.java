package com.arena.core.config;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import com.arena.core.exception.ApiErrors;
import com.arena.core.exception.ErrorCode;

/** Bounded, per-peer fixed-window abuse guard. Does not trust forwarded IP headers or retain request data. */
@Component
public class PublicCheckinRateLimit extends OncePerRequestFilter {
 private record Bucket(long starts,int count){}
 private final Map<String,Bucket> peers=new HashMap<>();
 private final ApiErrors errors;
 public PublicCheckinRateLimit(ApiErrors errors){this.errors=errors;}
 private synchronized boolean allow(String peer,long now){
  peers.entrySet().removeIf(e->now-e.getValue().starts>=600000);
  var b=peers.get(peer);if(b==null){if(peers.size()>=10000)return false;peers.put(peer,new Bucket(now,1));return true;}
  if(b.count>=600)return false;peers.put(peer,new Bucket(b.starts,b.count+1));return true;
 }
 @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws IOException,ServletException {
  var path=request.getRequestURI();
  if(!Set.of("/api/public/facility-check-ins/inspect","/api/public/facility-check-ins/submit","/public/facility-check-ins/inspect","/public/facility-check-ins/submit","/api/public/club","/public/club","/api/public/club/logo","/public/club/logo","/api/public/club/reservations","/public/club/reservations").contains(path)){chain.doFilter(request,response);return;}
  response.setHeader("Cache-Control","no-store");response.setHeader("Referrer-Policy","no-referrer");
  if(!allow(request.getRemoteAddr(),System.currentTimeMillis())){response.setHeader("Retry-After","600");errors.write(request,response,ErrorCode.TOO_MANY_REQUESTS);return;}
  if(request.getContentLengthLong()>2048){errors.write(request,response,ErrorCode.INVALID_REQUEST);return;}
  byte[] body=request.getInputStream().readNBytes(2049);
  if(body.length>2048){errors.write(request,response,ErrorCode.INVALID_REQUEST);return;}
  var stream=new java.io.ByteArrayInputStream(body);
  var bounded=new HttpServletRequestWrapper(request){
   @Override public ServletInputStream getInputStream(){return new ServletInputStream(){public int read(){return stream.read();}public boolean isFinished(){return stream.available()==0;}public boolean isReady(){return true;}public void setReadListener(ReadListener listener){throw new UnsupportedOperationException();}};}
   @Override public long getContentLengthLong(){return body.length;}
   @Override public int getContentLength(){return body.length;}
  };
  chain.doFilter(bounded,response);
 }
}
