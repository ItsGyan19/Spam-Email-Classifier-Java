package com.classifier.spam.web;

import com.classifier.spam.service.emailclassification.EmailClassificationService;
import com.classifier.spam.service.emailclassification.EmailClassificationService.ClassificationResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ClassificationApiController {
    private final EmailClassificationService classificationService;

    public ClassificationApiController(EmailClassificationService classificationService) {
        this.classificationService = classificationService;
    }

    @PostMapping("/classify")
    public ClassificationResponse classify(@Valid @RequestBody ClassificationRequest request) {
        return classificationService.classifyEmail(request.getText());
    }

    @PostMapping("/batch")
    public Map<String, Object> classifyBatch(@Valid @RequestBody BatchClassificationRequest request) {
        List<ClassificationResponse> results = request.getEmails().stream()
                .map(classificationService::classifyEmail).toList();
        return Map.of("count", results.size(), "results", results);
    }

    @GetMapping("/metrics")
    public Map<String, Object> metrics() {
        return classificationService.getEvaluationMetrics();
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        Map<String, Object> result = new LinkedHashMap<>(classificationService.getModelStatistics());
        result.put("evaluation", classificationService.getEvaluationMetrics());
        return result;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok", "model", "ready");
    }
}
