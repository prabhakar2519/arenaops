package com.arena.core.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.arena.core.repository.AppUserRepository;
import com.arena.core.repository.CustomerRepository;
import org.junit.jupiter.api.Test;

class CustomerEmailValidatorTest {
  @Test
  void checksNormalizedEmailAcrossCustomersAndRegisteredUsers() {
    CustomerRepository customers = mock(CustomerRepository.class);
    AppUserRepository users = mock(AppUserRepository.class);
    CustomerEmailValidator validator = new CustomerEmailValidator(customers, users);
    when(customers.existsByNormalizedEmail("owner@example.com")).thenReturn(true);
    when(users.existsByNormalizedEmail("staff@example.com")).thenReturn(true);
    assertThat(validator.isRegistered(" Owner@Example.com ")).isTrue();
    assertThat(validator.isRegistered(" STAFF@EXAMPLE.COM ")).isTrue();
    assertThat(validator.isRegistered("new@example.com")).isFalse();
  }
}
