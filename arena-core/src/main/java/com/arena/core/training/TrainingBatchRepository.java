package com.arena.core.training;
public interface TrainingBatchRepository extends org.springframework.data.jpa.repository.JpaRepository<TrainingBatch,Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<TrainingBatch> {
 java.util.List<TrainingBatch> findByAcademyIdOrderByName(Long academyId);
 @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
 @org.springframework.data.jpa.repository.Query("select batch from TrainingBatch batch where batch.id = :id and batch.academyId = :academyId")
 java.util.Optional<TrainingBatch> findByIdAndAcademyIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id,@org.springframework.data.repository.query.Param("academyId") Long academyId);
}
