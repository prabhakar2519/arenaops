package com.arena.core.checkin;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
@Component @RequiredArgsConstructor
public class FacilityCheckinRetention {
 private final FacilityCheckinService service;
 @Scheduled(fixedDelayString="${arena.checkin.cleanup-ms:300000}") public void cleanup(){service.cleanup();}
}
