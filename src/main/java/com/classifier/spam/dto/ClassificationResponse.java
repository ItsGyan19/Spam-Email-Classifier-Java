package com.classifier.spam.dto;

import com.classifier.spam.service.emailclassification.NaiveBayesClassifierService.Explanation;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Detailed classification response for an email.
 */
public record ClassificationResponse(
        @JsonProperty("label") String label,
        @JsonProperty("is_spam") boolean isSpam,
        @JsonProperty("confidence") double confidence,
        @JsonProperty("confidence_percent") double confidencePercent,
        @JsonProperty("spam_probability") double spamProbability,
        @JsonProperty("ham_probability") double hamProbability,
        @JsonProperty("topic") String topic,
        @JsonProperty("topic_display") String topicDisplay,
        @JsonProperty("topic_confidence") double topicConfidence,
        @JsonProperty("explanation") Explanation explanation,
        @JsonProperty("heuristics") Map<String, Object> heuristics
) { }
