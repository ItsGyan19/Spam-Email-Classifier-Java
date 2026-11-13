package com.classifier.spam.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ClassificationRequest {
    @NotBlank(message = "No email text provided")
    @Size(max = 50_000, message = "Email text exceeds the maximum allowed limit of 50,000 characters")
    private String text;

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
}
