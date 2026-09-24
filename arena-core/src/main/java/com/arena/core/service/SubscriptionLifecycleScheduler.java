package com.arena.core.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SubscriptionLifecycleScheduler {

  private final AdminCustomerService adminCustomerService;

  @Scheduled(fixedDelayString = "${app.lifecycle.expiry-scan-ms:300000}")
  public void expireTrialsAndGracePeriods() {
    adminCustomerService.expireTrialsAndGracePeriods();
  }
}
