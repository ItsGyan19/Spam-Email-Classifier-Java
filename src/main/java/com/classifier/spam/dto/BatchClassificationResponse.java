package com.classifier.spam.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response payload for batch email classification.
 */
public record BatchClassificationResponse(
        @JsonProperty("count") int count,
        @JsonProperty("results") List<ClassificationResponse> results
) { }
