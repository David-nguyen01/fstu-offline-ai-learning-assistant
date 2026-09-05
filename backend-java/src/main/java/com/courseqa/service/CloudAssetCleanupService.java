package com.courseqa.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.courseqa.model.entity.CloudAssetCleanupJob;
import com.courseqa.repository.CloudAssetCleanupJobRepository;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Durable outbox for Cloudinary deletion. Jobs remain pending until confirmed. */
@Slf4j
@Service
public class CloudAssetCleanupService {
    static final String PENDING = "PENDING";
    static final String COMPLETED = "COMPLETED";
    static final String RAW = "raw";

    private final CloudAssetCleanupJobRepository jobs;
    private final Cloudinary cloudinary;

    @Autowired
    public CloudAssetCleanupService(
            CloudAssetCleanupJobRepository jobs,
            @Value("${cloudinary.cloud-name:}") String cloudName,
            @Value("${cloudinary.api-key:}") String apiKey,
            @Value("${cloudinary.api-secret:}") String apiSecret) {
        this(jobs, createCloudinary(cloudName, apiKey, apiSecret));
    }

    CloudAssetCleanupService(CloudAssetCleanupJobRepository jobs, Cloudinary cloudinary) {
        this.jobs = jobs;
        this.cloudinary = cloudinary;
    }

    @Transactional
    public void enqueueRaw(String publicId) {
        if (publicId == null || publicId.isBlank()) return;
        String normalizedId = publicId.trim();
        if (jobs.findFirstByPublicIdAndResourceTypeAndStatus(normalizedId, RAW, PENDING).isPresent()) return;

        LocalDateTime now = LocalDateTime.now();
        CloudAssetCleanupJob job = new CloudAssetCleanupJob();
        job.setPublicId(normalizedId);
        job.setResourceType(RAW);
        job.setStatus(PENDING);
        job.setAttemptCount(0);
        job.setNextAttemptAt(now);
        job.setCreatedAt(now);
        job.setUpdatedAt(now);
        // Cloudinary deletion is idempotent ("not found" is a success), so a
        // rare concurrent duplicate is safer than making the caller's delete
        // transaction rollback-only because of a unique-index race.
        jobs.save(job);
    }

    public int retryPending() {
        List<CloudAssetCleanupJob> due = jobs
                .findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(PENDING, LocalDateTime.now());
        int completed = 0;
        for (CloudAssetCleanupJob job : due) {
            if (attempt(job)) completed++;
        }
        return completed;
    }

    private boolean attempt(CloudAssetCleanupJob job) {
        int attempt = Optional.ofNullable(job.getAttemptCount()).orElse(0) + 1;
        LocalDateTime now = LocalDateTime.now();
        try {
            if (cloudinary == null) {
                throw new IllegalStateException("Cloudinary credentials are not configured.");
            }
            Map<?, ?> result = cloudinary.uploader().destroy(
                    job.getPublicId(),
                    ObjectUtils.asMap("resource_type", job.getResourceType()));
            String outcome = String.valueOf(result.get("result")).trim().toLowerCase(Locale.ROOT);
            if (!"ok".equals(outcome) && !"not found".equals(outcome)) {
                throw new IOException("Cloudinary returned result=" + outcome);
            }

            job.setStatus(COMPLETED);
            job.setAttemptCount(attempt);
            job.setLastError(null);
            job.setCompletedAt(now);
            job.setUpdatedAt(now);
            jobs.save(job);
            log.info("Cloudinary asset cleanup completed for {} ({}).", job.getPublicId(), outcome);
            return true;
        } catch (IOException | RuntimeException exception) {
            long delaySeconds = Math.min(3600L, 30L << Math.min(Math.max(0, attempt - 1), 7));
            job.setStatus(PENDING);
            job.setAttemptCount(attempt);
            job.setLastError(compactError(exception));
            job.setNextAttemptAt(now.plusSeconds(delaySeconds));
            job.setUpdatedAt(now);
            jobs.save(job);
            log.warn("Cloudinary cleanup attempt {} failed for {}; retrying in {} seconds.",
                    attempt, job.getPublicId(), delaySeconds, exception);
            return false;
        }
    }

    private String compactError(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) message = exception.getClass().getSimpleName();
        return message.substring(0, Math.min(message.length(), 1000));
    }

    private static Cloudinary createCloudinary(String cloudName, String apiKey, String apiSecret) {
        if (isBlank(cloudName) || isBlank(apiKey) || isBlank(apiSecret)) return null;
        return new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key", apiKey,
                "api_secret", apiSecret,
                "secure", true));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
