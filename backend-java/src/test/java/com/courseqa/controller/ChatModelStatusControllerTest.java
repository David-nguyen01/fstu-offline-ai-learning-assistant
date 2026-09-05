package com.courseqa.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.courseqa.service.EvaluationService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatModelStatusControllerTest {
    @Mock
    EvaluationService evaluationService;

    @Test
    void exposesReadinessWithoutLeakingRuntimePathsOrManifestDetails() {
        when(evaluationService.modelReadiness()).thenReturn(Map.of(
                "reachable", true,
                "fineTunedReady", false,
                "fineTunedStatus", "QUALITY_GATE_FAILED",
                "details", Map.of("adapter_dir", "D:/private/model")));

        var response = new ChatModelStatusController(evaluationService).modelStatus();
        var body = response.getBody();

        assertThat(body).isNotNull();
        assertThat(body.getData())
                .containsEntry("reachable", true)
                .containsEntry("fineTunedReady", false)
                .containsEntry("fineTunedStatus", "QUALITY_GATE_FAILED")
                .doesNotContainKey("details");
    }
}
