package com.arena.core.training;
public interface AcademyStaffRepository extends org.springframework.data.jpa.repository.JpaRepository<AcademyStaff,Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<AcademyStaff> {
 java.util.Optional<AcademyStaff> findByAppUserId(Long appUserId);
 java.util.Optional<AcademyStaff> findByAcademyIdAndRegistrationRequestId(Long academyId,String requestId);
 java.util.Optional<AcademyStaff> findByLoginEmail(String email);
 java.util.Optional<AcademyStaff> findByLoginUsernameIgnoreCase(String username);
 java.util.List<AcademyStaff> findByAcademyIdOrderByName(Long academyId);
 java.util.Optional<AcademyStaff> findByAcademyIdAndAppUserId(Long academyId, Long appUserId);
}
