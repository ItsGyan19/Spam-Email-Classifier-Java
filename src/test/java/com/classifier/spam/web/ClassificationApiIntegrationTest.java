package com.classifier.spam.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ClassificationApiIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void trainingDatasetIsLoadedForBothModels() throws Exception {
        mockMvc.perform(get("/api/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSamples").value(2_080))
                .andExpect(jsonPath("$.totalHamSamples", greaterThan(0)))
                .andExpect(jsonPath("$.totalSpamSamples", greaterThan(0)))
                .andExpect(jsonPath("$.vocabularySize", greaterThan(0)))
                .andExpect(jsonPath("$.topicCount").value(8));
    }

    @Test
    void classifiesSpamAndReturnsTopicHeuristicsAndExplainability() throws Exception {
        mockMvc.perform(classify("CONGRATULATIONS! You won $2,000,000. Wire transfer available now!"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("spam"))
                .andExpect(jsonPath("$.is_spam").value(true))
                .andExpect(jsonPath("$.spam_probability").isNumber())
                .andExpect(jsonPath("$.topic").isString())
                .andExpect(jsonPath("$.topic_confidence").isNumber())
                .andExpect(jsonPath("$.explanation.top_spam_words").isArray())
                .andExpect(jsonPath("$.heuristics.currency_count").value(1));
    }

    @Test
    void classifiesLegitimateEmailAsHam() throws Exception {
        mockMvc.perform(classify("Hi team, the code review is complete. Can we meet tomorrow at 10 AM?"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("ham"))
                .andExpect(jsonPath("$.is_spam").value(false))
                .andExpect(jsonPath("$.confidence", greaterThan(0.5)));
    }

    @Test
    void returnsNeutralResponseForUnicodeAndNullByteInput() throws Exception {
        mockMvc.perform(classify("Hi 😊 こんにちは\u0000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").isString())
                .andExpect(jsonPath("$.heuristics").exists());
    }

    @Test
    void flagsMixedScriptDomainsThatCanImpersonateTrustedSites() throws Exception {
        mockMvc.perform(classify("Verify your account at https://pаypal.com/login"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.heuristics.suspicious_unicode_url_count").value(1))
                .andExpect(jsonPath("$.spam_probability", greaterThan(0.74)));
    }

    @Test
    void checksLongMessagesForSpamBeforeBenignPadding() throws Exception {
        String paddedMessage = "URGENT! Your account is locked. Verify your account and claim your prize now! "
                + "Thank you for your message. The team will review the project notes and reply during our next meeting. ".repeat(35);

        mockMvc.perform(classify(paddedMessage))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.spam_probability", greaterThan(0.74)));
    }

    @Test
    void rejectsEmptyAndOversizedEmails() throws Exception {
        mockMvc.perform(post("/api/classify").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("text", "  "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("No email text provided"));

        mockMvc.perform(classify("a".repeat(50_001)))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.error").value("Email text exceeds the maximum allowed limit of 50,000 characters"));
    }

    @Test
    void batchClassifiesEmailsAndEnforcesBatchLimit() throws Exception {
        String request = objectMapper.writeValueAsString(Map.of("emails", List.of(
                "URGENT: verify your account and claim your prize now!",
                "Hi team, the meeting is moved to 2 PM tomorrow.")));
        mockMvc.perform(post("/api/batch").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.results.length()").value(2));

        String oversizedBatch = objectMapper.writeValueAsString(Map.of("emails", java.util.Collections.nCopies(101, "meeting")));
        mockMvc.perform(post("/api/batch").contentType(MediaType.APPLICATION_JSON).content(oversizedBatch))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Batch size exceeds maximum limit of 100 emails"));
    }

    @Test
    void metricsEndpointReportsMeasuredJavaHoldoutResults() throws Exception {
        mockMvc.perform(get("/api/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.best_algorithm").value("MultinomialNB"))
                .andExpect(jsonPath("$.evaluation_method").value("stratified 80/20 holdout (seed 42)"))
                .andExpect(jsonPath("$.test_metrics.accuracy").isNumber())
                .andExpect(jsonPath("$.test_metrics.precision").isNumber())
                .andExpect(jsonPath("$.test_metrics.recall").isNumber())
                .andExpect(jsonPath("$.test_metrics.f1").isNumber())
                .andExpect(jsonPath("$.test_metrics.roc_auc").isNumber())
                .andExpect(jsonPath("$.topic_classes", hasItem("phishing")));
    }

    @Test
    void rendersDashboardAndAppliesSecurityHeaders() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Email Safety Checker")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().exists("Content-Security-Policy"))
                .andExpect(header().string("Permissions-Policy", "camera=(), microphone=(), geolocation=()"));
    }

    @Test
    void malformedJsonReturnsSafeError() throws Exception {
        mockMvc.perform(post("/api/classify").contentType(MediaType.APPLICATION_JSON).content("{bad json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Request body must be valid JSON with the expected fields"));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder classify(String text) throws Exception {
        return post("/api/classify").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("text", text)));
    }
}
