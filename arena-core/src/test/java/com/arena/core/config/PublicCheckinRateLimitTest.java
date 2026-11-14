package com.arena.core.config;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import com.arena.core.exception.ApiErrors;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
class PublicCheckinRateLimitTest {
 @Test void boundedPublicBodiesAreRejectedEvenWithoutContentLength()throws Exception{var filter=new PublicCheckinRateLimit(new ApiErrors(new ObjectMapper()));var req=new MockHttpServletRequest("POST","/api/public/facility-check-ins/submit"){@Override public long getContentLengthLong(){return -1;}};req.setContent(new byte[2049]);var res=new MockHttpServletResponse();filter.doFilter(req,res,(a,b)->fail("Oversized body reached controller"));assertEquals(400,res.getStatus());}
 @Test void publicRequestsAreRateLimitedWithoutBlockingPrivateOperations()throws Exception{var filter=new PublicCheckinRateLimit(new ApiErrors(new ObjectMapper()));int[] count={0};for(int i=0;i<601;i++){var req=new MockHttpServletRequest("POST","/api/public/facility-check-ins/inspect");req.setContent("{}".getBytes());var res=new MockHttpServletResponse();filter.doFilter(req,res,(a,b)->count[0]++);if(i==600)assertEquals(429,res.getStatus());}assertEquals(600,count[0]);var privateReq=new MockHttpServletRequest("GET","/api/academy/facility-check-ins");filter.doFilter(privateReq,new MockHttpServletResponse(),(a,b)->count[0]++);assertEquals(601,count[0]);}
}
