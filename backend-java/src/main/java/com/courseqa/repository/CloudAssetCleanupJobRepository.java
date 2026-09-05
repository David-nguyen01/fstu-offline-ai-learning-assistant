package com.courseqa.repository;

import com.courseqa.model.entity.CloudAssetCleanupJob;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CloudAssetCleanupJobRepository extends JpaRepository<CloudAssetCleanupJob, UUID> {
    Optional<CloudAssetCleanupJob> findFirstByPublicIdAndResourceTypeAndStatus(
            String publicId, String resourceType, String status);

    List<CloudAssetCleanupJob> findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            String status, LocalDateTime now);
}
