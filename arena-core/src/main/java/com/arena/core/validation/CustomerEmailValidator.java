package com.arena.core.validation;

import com.arena.core.exception.ArenaOpsException;
import com.arena.core.exception.ErrorCode;

import com.arena.core.repository.AppUserRepository;
import com.arena.core.repository.CustomerRepository;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CustomerEmailValidator {
  private final CustomerRepository customerRepository;
  private final AppUserRepository appUserRepository;

  public boolean isRegistered(String email) {
    String normalized = email.trim().toLowerCase(Locale.ROOT);
    return customerRepository.existsByNormalizedEmail(normalized)
        || appUserRepository.existsByNormalizedEmail(normalized);
  }

  public void assertAvailable(String email) {
    if (isRegistered(email)) {
      throw new ArenaOpsException(ErrorCode.CUSTOMER_ALREADY_EXISTS);
    }
  }
}
