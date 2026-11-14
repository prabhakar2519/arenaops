package com.arena.core.billing;

import static com.arena.core.billing.AcademyBillingContracts.PaymentRequest;
import static com.arena.core.billing.AcademyBillingContracts.PaymentView;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.arena.core.entity.AppUserEntity;
import com.arena.core.exception.ArenaOpsException;
import com.arena.core.exception.ErrorCode;
import com.arena.core.students.AcademyEnrollmentRepository;
import com.arena.core.students.AcademyStudentRepository;
import com.arena.core.training.AcademyAuthorization;
import com.arena.core.training.TrainingBatchRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class AcademyBillingServiceTest {
  @Mock private AcademyAuthorization authorization;
  @Mock private AcademyStudentRepository students;
  @Mock private TrainingBatchRepository batches;
  @Mock private AcademyEnrollmentRepository enrollments;
  @Mock private AcademyFeeChargeRepository charges;
  @Mock private AcademyPaymentRepository payments;
  @Mock private AcademyPaymentAllocationRepository allocations;
  private AcademyBillingService service;
  private Jwt jwt;

  @BeforeEach
  void setUp() {
    service = new AcademyBillingService(authorization, students, batches, enrollments, charges, payments, allocations);
    jwt = Jwt.withTokenValue("token").header("alg", "none").subject("owner").build();
    AppUserEntity owner = AppUserEntity.builder().id(1L).username("owner").customerId(12L).build();
    when(authorization.manage(jwt)).thenReturn(new AcademyAuthorization.Actor(owner, null, Set.of("OWNER")));
    AcademyFeeCharge charge = new AcademyFeeCharge();
    charge.setId(8L); charge.setAcademyId(12L); charge.setStudentId(41L); charge.setAmount(new BigDecimal("100.00"));
    charge.setDueDate(LocalDate.now().plusDays(2));
    when(charges.findByIdAndAcademyIdForUpdate(8L, 12L)).thenReturn(Optional.of(charge));
  }

  @Test
  void partialPaymentAllocatesOnlyTheRecordedAmount() {
    when(allocations.totalForCharge(12L, 8L)).thenReturn(new BigDecimal("25.00"));
    when(payments.saveAndFlush(any())).thenAnswer(invocation -> {
      AcademyPayment payment = invocation.getArgument(0);
      payment.setId(19L);
      return payment;
    });

    PaymentView result = service.recordPayment(jwt, 8L,
        new PaymentRequest(new BigDecimal("30.00"), LocalDate.now(), "UPI", "ref-1", null));

    assertEquals(19L, result.id());
    assertEquals(new BigDecimal("30.00"), result.amount());
    ArgumentCaptor<AcademyPaymentAllocation> allocation = ArgumentCaptor.forClass(AcademyPaymentAllocation.class);
    verify(allocations).save(allocation.capture());
    assertEquals(new BigDecimal("30.00"), allocation.getValue().getAmount());
    assertEquals(8L, allocation.getValue().getChargeId());
  }

  @Test
  void overpaymentIsRejectedWithoutCreatingAPayment() {
    when(allocations.totalForCharge(12L, 8L)).thenReturn(new BigDecimal("75.00"));

    ArenaOpsException error = assertThrows(ArenaOpsException.class, () -> service.recordPayment(jwt, 8L,
        new PaymentRequest(new BigDecimal("25.01"), LocalDate.now(), "CASH", null, null)));

    assertEquals(ErrorCode.INVALID_REQUEST, error.getErrorCode());
    verify(payments, never()).saveAndFlush(any());
    verify(allocations, never()).save(any());
  }
}