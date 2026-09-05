package com.courseqa.repository;

import com.courseqa.model.entity.UploadSession;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UploadSessionRepository extends JpaRepository<UploadSession, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from UploadSession session where session.uploadId = :uploadId")
    java.util.Optional<UploadSession> findForUpdate(@Param("uploadId") UUID uploadId);

    List<UploadSession> findByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, String status);

    List<UploadSession> findByStatusAndUpdatedAtBefore(String status, LocalDateTime cutoff);
}
