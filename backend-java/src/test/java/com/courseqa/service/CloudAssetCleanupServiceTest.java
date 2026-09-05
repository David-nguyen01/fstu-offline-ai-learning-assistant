package com.courseqa.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.courseqa.model.entity.CloudAssetCleanupJob;
import com.courseqa.repository.CloudAssetCleanupJobRepository;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CloudAssetCleanupServiceTest {
    private final CloudAssetCleanupJobRepository jobs = mock(CloudAssetCleanupJobRepository.class);
    private final Cloudinary cloudinary = mock(Cloudinary.class);
    private final Uploader uploader = mock(Uploader.class);
    private CloudAssetCleanupService service;

    @BeforeEach
    void setUp() {
        when(cloudinary.uploader()).thenReturn(uploader);
        service = new CloudAssetCleanupService(jobs, cloudinary);
    }

    @Test
    void enqueuePersistsANewPendingJob() {
        when(jobs.findFirstByPublicIdAndResourceTypeAndStatus(
                "folder/document", "raw", "PENDING")).thenReturn(Optional.empty());

        service.enqueueRaw("  folder/document  ");

        ArgumentCaptor<CloudAssetCleanupJob> captor = ArgumentCaptor.forClass(CloudAssetCleanupJob.class);
        verify(jobs).save(captor.capture());
        CloudAssetCleanupJob saved = captor.getValue();
        assertEquals("folder/document", saved.getPublicId());
        assertEquals("raw", saved.getResourceType());
        assertEquals("PENDING", saved.getStatus());
        assertEquals(0, saved.getAttemptCount());
        assertNotNull(saved.getNextAttemptAt());
    }

    @Test
    void enqueueIgnoresBlankAndAlreadyPendingAssets() {
        service.enqueueRaw(" ");
        verify(jobs, never()).save(any());

        when(jobs.findFirstByPublicIdAndResourceTypeAndStatus(
                "same-id", "raw", "PENDING")).thenReturn(Optional.of(pending("same-id")));
        service.enqueueRaw("same-id");
        verify(jobs, never()).save(any());
    }

    @Test
    void retryMarksConfirmedDeletionCompleted() throws IOException {
        CloudAssetCleanupJob job = pending("folder/document");
        when(jobs.findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                eq("PENDING"), any(LocalDateTime.class))).thenReturn(List.of(job));
        when(uploader.destroy(eq("folder/document"), anyMap())).thenReturn(Map.of("result", "ok"));

        assertEquals(1, service.retryPending());

        assertEquals("COMPLETED", job.getStatus());
        assertEquals(1, job.getAttemptCount());
        assertNotNull(job.getCompletedAt());
        assertNull(job.getLastError());
        verify(jobs).save(job);
    }

    @Test
    void retryTreatsAlreadyMissingAssetAsCompleted() throws IOException {
        CloudAssetCleanupJob job = pending("already-gone");
        when(jobs.findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                eq("PENDING"), any(LocalDateTime.class))).thenReturn(List.of(job));
        when(uploader.destroy(eq("already-gone"), anyMap())).thenReturn(Map.of("result", "not found"));

        assertEquals(1, service.retryPending());
        assertEquals("COMPLETED", job.getStatus());
    }

    @Test
    void retryKeepsFailedDeletionPendingWithBackoff() throws IOException {
        CloudAssetCleanupJob job = pending("temporary-failure");
        when(jobs.findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                eq("PENDING"), any(LocalDateTime.class))).thenReturn(List.of(job));
        when(uploader.destroy(eq("temporary-failure"), anyMap()))
                .thenThrow(new IOException("provider unavailable"));
        LocalDateTime before = LocalDateTime.now();

        assertEquals(0, service.retryPending());

        assertEquals("PENDING", job.getStatus());
        assertEquals(1, job.getAttemptCount());
        assertEquals("provider unavailable", job.getLastError());
        assertTrue(job.getNextAttemptAt().isAfter(before.plusSeconds(20)));
        assertNull(job.getCompletedAt());
        verify(jobs).save(job);
    }

    private CloudAssetCleanupJob pending(String publicId) {
        LocalDateTime now = LocalDateTime.now().minusMinutes(1);
        CloudAssetCleanupJob job = new CloudAssetCleanupJob();
        job.setPublicId(publicId);
        job.setResourceType("raw");
        job.setStatus("PENDING");
        job.setAttemptCount(0);
        job.setCreatedAt(now);
        job.setUpdatedAt(now);
        job.setNextAttemptAt(now);
        return job;
    }
}
