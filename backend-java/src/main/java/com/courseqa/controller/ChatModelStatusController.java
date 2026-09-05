package com.courseqa.controller;

import com.courseqa.model.dto.ApiResponse;
import com.courseqa.service.EvaluationService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes only the model-readiness fields needed by the authenticated chat UI. */
@RestController
@RequestMapping("/api/chat")
public class ChatModelStatusController {
    private final EvaluationService evaluationService;

    public ChatModelStatusController(EvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    @GetMapping("/model-status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> modelStatus() {
        Map<String, Object> readiness = evaluationService.modelReadiness();
        Map<String, Object> safe = new LinkedHashMap<>();
        copy(readiness, safe, "reachable");
        copy(readiness, safe, "baseRagReady");
        copy(readiness, safe, "baseRagStatus");
        copy(readiness, safe, "fineTunedReady");
        copy(readiness, safe, "fineTunedStatus");
        copy(readiness, safe, "modelVerificationStatus");
        copy(readiness, safe, "qualityGatePassed");
        copy(readiness, safe, "trainingReady");
        copy(readiness, safe, "trainingBlocker");
        return ResponseEntity.ok(ApiResponse.ok(safe));
    }

    private static void copy(Map<String, Object> source, Map<String, Object> target, String key) {
        if (source.containsKey(key)) target.put(key, source.get(key));
    }
}
