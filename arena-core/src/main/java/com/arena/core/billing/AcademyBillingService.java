package com.arena.core.billing;

import static com.arena.core.billing.AcademyBillingContracts.*;

import com.arena.core.exception.ArenaOpsException;
import com.arena.core.exception.ErrorCode;
import com.arena.core.students.AcademyEnrollmentRepository;
import com.arena.core.students.AcademyStudent;
import com.arena.core.students.AcademyStudentRepository;
import com.arena.core.training.AcademyAuthorization;
import com.arena.core.training.TrainingBatch;
import com.arena.core.training.TrainingBatchRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional
public class AcademyBillingService {
  private final AcademyAuthorization authorization;
  private final AcademyStudentRepository students;
  private final TrainingBatchRepository batches;
  private final AcademyEnrollmentRepository enrollments;
  private final AcademyFeeChargeRepository charges;
  private final AcademyPaymentRepository payments;
  private final AcademyPaymentAllocationRepository allocations;

  public AcademyBillingService(AcademyAuthorization authorization, AcademyStudentRepository students,
      TrainingBatchRepository batches, AcademyEnrollmentRepository enrollments, AcademyFeeChargeRepository charges,
      AcademyPaymentRepository payments, AcademyPaymentAllocationRepository allocations) {
    this.authorization = authorization; this.students = students; this.batches = batches; this.enrollments = enrollments;
    this.charges = charges; this.payments = payments; this.allocations = allocations;
  }

  public ChargeView createCharge(Jwt jwt, ChargeRequest request) {
    var actor = authorization.manage(jwt);
    AcademyStudent student = student(request.studentId(), actor.academyId());
    TrainingBatch batch = request.batchId() == null ? null : batch(request.batchId(), actor.academyId());
    if (batch != null && !enrollments.existsByAcademyIdAndStudentIdAndBatchIdAndStatus(actor.academyId(), student.getId(), batch.getId(), "ACTIVE")) {
      throw new ArenaOpsException(ErrorCode.INVALID_STATE);
    }
    return charge(createCharge(actor.academyId(), student, batch, request.description(), request.amount(), request.dueDate(), actor.user().getUsername()));
  }

  public List<ChargeView> chargeActiveBatch(Jwt jwt, Long batchId, BatchChargeRequest request) {
    var actor = authorization.manage(jwt);
    TrainingBatch batch = batch(batchId, actor.academyId());
    List<ChargeView> result = new ArrayList<>();
    enrollments.findByAcademyIdAndBatchIdOrderByCreatedAtDesc(actor.academyId(), batchId).stream()
        .filter(enrollment -> "ACTIVE".equals(enrollment.getStatus()))
        .forEach(enrollment -> result.add(charge(createCharge(actor.academyId(), student(enrollment.getStudentId(), actor.academyId()),
            batch, request.description(), request.amount(), request.dueDate(), actor.user().getUsername()))));
    return result;
  }

  public List<ChargeView> list(Jwt jwt, Long studentId, Long batchId) {
    Long academyId = authorization.manage(jwt).academyId();
    if (studentId != null) student(studentId, academyId);
    if (batchId != null) batch(batchId, academyId);
    return charges.findByAcademyIdOrderByDueDateAsc(academyId).stream()
        .filter(charge -> studentId == null || Objects.equals(studentId, charge.getStudentId()))
        .filter(charge -> batchId == null || Objects.equals(batchId, charge.getBatchId()))
        .map(this::charge).toList();
  }

  public PaymentView recordPayment(Jwt jwt, Long chargeId, PaymentRequest request) {
    var actor = authorization.manage(jwt);
    AcademyFeeCharge charge = charges.findByIdAndAcademyIdForUpdate(chargeId, actor.academyId())
        .orElseThrow(() -> new ArenaOpsException(ErrorCode.RESOURCE_NOT_FOUND));
    BigDecimal alreadyCollected = zero(allocations.totalForCharge(actor.academyId(), chargeId));
    BigDecimal outstanding = charge.getAmount().subtract(alreadyCollected);
    if (request.amount().compareTo(outstanding) > 0) throw new ArenaOpsException(ErrorCode.INVALID_REQUEST);
    AcademyPayment payment = new AcademyPayment();
    payment.setAcademyId(actor.academyId()); payment.setStudentId(charge.getStudentId());
    payment.setAmount(request.amount()); payment.setPaymentDate(request.paymentDate() == null ? LocalDate.now() : request.paymentDate());
    payment.setMethod(request.method()); payment.setReference(clean(request.reference())); payment.setNotes(clean(request.notes()));
    payment.setRecordedBy(actor.user().getUsername());
    payment = payments.saveAndFlush(payment);
    AcademyPaymentAllocation allocation = new AcademyPaymentAllocation();
    allocation.setAcademyId(actor.academyId()); allocation.setPaymentId(payment.getId()); allocation.setChargeId(chargeId);
    allocation.setAmount(request.amount()); allocation.setCreatedAt(LocalDateTime.now());
    allocations.save(allocation);
    return new PaymentView(payment.getId(), payment.getAmount(), payment.getPaymentDate(), payment.getMethod(), payment.getReference(), payment.getNotes());
  }

  public List<PaymentView> paymentHistory(Jwt jwt, Long studentId) {
    Long academyId = authorization.manage(jwt).academyId();
    student(studentId, academyId);
    return payments.findByAcademyIdAndStudentIdOrderByPaymentDateDescRecordedAtDesc(academyId, studentId).stream()
        .map(payment -> new PaymentView(payment.getId(), payment.getAmount(), payment.getPaymentDate(), payment.getMethod(), payment.getReference(), payment.getNotes())).toList();
  }

  public Summary summary(Jwt jwt) {
    Long academyId = authorization.manage(jwt).academyId();
    List<AcademyFeeCharge> all = charges.findByAcademyIdOrderByDueDateAsc(academyId);
    BigDecimal charged = all.stream().map(AcademyFeeCharge::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal collected = zero(allocations.totalForAcademy(academyId));
    BigDecimal outstanding = charged.subtract(collected).max(BigDecimal.ZERO);
    long open = all.stream().filter(charge -> charge(charge).outstanding().signum() > 0).count();
    long overdue = all.stream().filter(charge -> charge.getDueDate().isBefore(LocalDate.now()) && charge(charge).outstanding().signum() > 0).count();
    return new Summary(charged, collected, outstanding, open, overdue);
  }

  private AcademyFeeCharge createCharge(Long academyId, AcademyStudent student, TrainingBatch batch, String description,
      BigDecimal amount, LocalDate dueDate, String actor) {
    AcademyFeeCharge charge = new AcademyFeeCharge(); charge.setAcademyId(academyId); charge.setStudentId(student.getId());
    charge.setBatchId(batch == null ? null : batch.getId()); charge.setDescription(description.strip()); charge.setAmount(amount);
    charge.setDueDate(dueDate); charge.setCreatedBy(actor);
    return charges.saveAndFlush(charge);
  }

  private ChargeView charge(AcademyFeeCharge charge) {
    AcademyStudent student = student(charge.getStudentId(), charge.getAcademyId());
    BigDecimal collected = zero(allocations.totalForCharge(charge.getAcademyId(), charge.getId()));
    BigDecimal outstanding = charge.getAmount().subtract(collected).max(BigDecimal.ZERO);
    String status = outstanding.signum() == 0 ? "PAID" : collected.signum() > 0 ? "PARTIALLY_PAID"
        : charge.getDueDate().isBefore(LocalDate.now()) ? "OVERDUE" : "DUE";
    return new ChargeView(charge.getId(), student.getId(), student.getStudentCode(), student.getFirstName() + " " + student.getLastName(),
        charge.getBatchId(), charge.getDescription(), charge.getAmount(), collected, outstanding, charge.getDueDate(), status);
  }

  private AcademyStudent student(Long id, Long academyId) {
    return students.findByIdAndAcademyId(id, academyId).orElseThrow(() -> new ArenaOpsException(ErrorCode.RESOURCE_NOT_FOUND));
  }
  private TrainingBatch batch(Long id, Long academyId) {
    return batches.findById(id).filter(value -> academyId.equals(value.getAcademyId())).orElseThrow(() -> new ArenaOpsException(ErrorCode.RESOURCE_NOT_FOUND));
  }
  private BigDecimal zero(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
  private String clean(String value) { return value == null || value.isBlank() ? null : value.strip(); }
}