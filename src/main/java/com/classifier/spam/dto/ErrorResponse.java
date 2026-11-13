package com.classifier.spam.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Standard error response envelope.
 */
public record ErrorResponse(
        @JsonProperty("error") String error,
        @JsonProperty("status") int status,
        @JsonProperty("timestamp") long timestamp
) {
    public ErrorResponse(String error, int status) {
        this(error, status, System.currentTimeMillis());
    }
}
