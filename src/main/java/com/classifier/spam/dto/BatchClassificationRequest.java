package com.classifier.spam.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request payload for batch email classification.
 */
public class BatchClassificationRequest {

    @NotEmpty(message = "Provide a non-empty array of email texts in 'emails'")
    @Size(max = 100, message = "Batch size exceeds maximum limit of 100 emails")
    private List<@NotNull @Size(max = 50_000, message = "Email text exceeds the 50,000 character limit") String> emails;

    public BatchClassificationRequest() { }

    public BatchClassificationRequest(List<String> emails) {
        this.emails = emails;
    }

    public List<String> getEmails() {
        return emails;
    }

    public void setEmails(List<String> emails) {
        this.emails = emails;
    }
}
