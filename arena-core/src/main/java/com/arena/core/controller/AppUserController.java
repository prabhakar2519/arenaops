package com.arena.core.controller;

import com.arena.core.model.AppUserRequest;
import com.arena.core.model.AppUserResponse;
import com.arena.core.service.AppUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
public class AppUserController {

  private final AppUserService appUserService;

  @PostMapping
  public ResponseEntity<AppUserResponse> saveUser(@Valid @RequestBody AppUserRequest request) {
    log.info("[AppUserController] POST /api/users - username={}, email={}, role={}",
        request.getUsername(), request.getEmail(), request.getRole());
    try {
      AppUserResponse response = appUserService.save(request);
      log.info("[AppUserController] POST /api/users SUCCESS - userId={}, username={}",
          response.getId(), response.getUsername());
      return ResponseEntity.status(HttpStatus.CREATED).body(response);
    } catch (Exception e) {
      log.error("[AppUserController] POST /api/users FAILED - username={}, error={}",
          request.getUsername(), e.getMessage(), e);
      throw e;
    }
  }
}
