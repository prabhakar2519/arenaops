package com.arena.core.service;

import com.arena.core.entity.AuditEventEntity;
import com.arena.core.enums.AuditActorType;
import com.arena.core.repository.AuditEventRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditService {

  private final AuditEventRepository auditEventRepository;

  public void record(String eventType, Long customerId, AuditActorType actorType, String actorId,
      String previousState, String newState, String reason, String metadata) {
    auditEventRepository.save(AuditEventEntity.builder()
        .eventType(eventType)
        .customerId(customerId)
        .actorType(actorType)
        .actorId(actorId)
        .occurredAt(LocalDateTime.now())
        .previousState(previousState)
        .newState(newState)
        .reason(reason)
        .metadata(metadata)
        .build());
  }
}
