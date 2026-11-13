package com.classifier.spam.service.emailclassification;

public record EmailTrainingExample(String label, String topic, String content) { }
