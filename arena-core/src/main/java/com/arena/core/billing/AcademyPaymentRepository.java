package com.arena.core.billing;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AcademyPaymentRepository extends JpaRepository<AcademyPayment, Long> {
  List<AcademyPayment> findByAcademyIdAndStudentIdOrderByPaymentDateDescRecordedAtDesc(Long academyId, Long studentId);
  List<AcademyPayment> findByAcademyIdAndSessionIdOrderByRecordedAtAsc(Long academyId,Long sessionId);
  @org.springframework.data.jpa.repository.Query("select p from AcademyPayment p where p.academyId=:academy and p.sessionId is not null and p.paymentDate>=:from and p.paymentDate<=:to order by p.recordedAt")
  List<AcademyPayment> sessionPayments(@org.springframework.data.repository.query.Param("academy")Long academy,@org.springframework.data.repository.query.Param("from")java.time.LocalDate from,@org.springframework.data.repository.query.Param("to")java.time.LocalDate to);
}