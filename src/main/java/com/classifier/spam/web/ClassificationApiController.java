package com.classifier.spam.web;

import com.classifier.spam.dto.BatchClassificationRequest;
import com.classifier.spam.dto.BatchClassificationResponse;
import com.classifier.spam.dto.ClassificationRequest;
import com.classifier.spam.dto.ClassificationResponse;
import com.classifier.spam.service.emailclassification.EmailClassificationService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API controller exposing endpoints for single and batch email classification,
 * model evaluation metrics, and system health status.
 */
@RestController
@RequestMapping("/api")
public class ClassificationApiController {

    private static final Logger log = LoggerFactory.getLogger(ClassificationApiController.class);

    private final EmailClassificationService classificationService;

    public ClassificationApiController(EmailClassificationService classificationService) {
        this.classificationService = classificationService;
    }

    @PostMapping("/classify")
    public ResponseEntity<ClassificationResponse> classify(@Valid @RequestBody ClassificationRequest request) {
        log.info("Classifying single email (length: {} chars)", request.getText().length());
        ClassificationResponse response = classificationService.classifyEmail(request.getText());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/batch")
    public ResponseEntity<BatchClassificationResponse> classifyBatch(@Valid @RequestBody BatchClassificationRequest request) {
        log.info("Processing batch classification for {} emails", request.getEmails().size());
        List<ClassificationResponse> results = request.getEmails().stream()
                .map(classificationService::classifyEmail)
                .toList();
        return ResponseEntity.ok(new BatchClassificationResponse(results.size(), results));
    }

    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> metrics() {
        return ResponseEntity.ok(classificationService.getEvaluationMetrics());
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats() {
        Map<String, Object> result = new LinkedHashMap<>(classificationService.getModelStatistics());
        result.put("evaluation", classificationService.getEvaluationMetrics());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "ok", "model", "ready"));
    }
}
