package com.courseqa.controller;

import com.courseqa.model.dto.ApiResponse;
import com.courseqa.model.entity.ProcessingJob;
import com.courseqa.service.DocumentProcessingService;
import com.courseqa.service.DocumentService;
import com.courseqa.security.JwtPrincipal;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

/**
 * Admin visibility into background document processing jobs (extraction, OCR,
 * chunking, embedding), so re-index/failures can be monitored and retried.
 */
@RestController
@RequestMapping("/api/admin/processing-jobs")
@CrossOrigin
public class ProcessingJobController {
    private final DocumentProcessingService documentProcessingService;
    private final DocumentService documentService;

    public ProcessingJobController(
            DocumentProcessingService documentProcessingService,
            DocumentService documentService) {
        this.documentProcessingService = documentProcessingService;
        this.documentService = documentService;
    }

    @GetMapping
    public ApiResponse<List<ProcessingJob>> listJobs() {
        return ApiResponse.ok(documentProcessingService.listJobs());
    }

    @GetMapping("/{jobId}")
    public ApiResponse<ProcessingJob> getJob(@PathVariable UUID jobId) {
        return ApiResponse.ok(documentProcessingService.getJob(jobId));
    }

    @PostMapping("/{jobId}/retry")
    public ApiResponse<ProcessingJob> retryJob(
            @PathVariable UUID jobId,
            @AuthenticationPrincipal JwtPrincipal principal) {
        ProcessingJob failedJob = documentProcessingService.getJob(jobId);
        documentService.requireReindexAccess(failedJob.getDocumentId(), principal.userId());
        return ApiResponse.ok(documentProcessingService.enqueueRetry(
                failedJob.getDocumentId(), principal.userId()));
    }
}
