package com.classifier.spam.service.emailclassification;

import com.classifier.spam.service.textprocessing.TextPreprocessingService;
import com.classifier.spam.service.textprocessing.TextPreprocessingService.ExtractedFeatures;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class EmailClassificationService {
    private static final int SEGMENT_LENGTH = 500;
    private static final int MIN_LONG_MESSAGE_LENGTH = 2_500;
    private static final double HIGH_SEGMENT_SPAM_THRESHOLD = 0.5;
    private static final double SUSPICIOUS_URL_SPAM_FLOOR = 0.75;
    private static final List<String> HIGH_RISK_SEGMENT_PHRASES = List.of(
            "claim your prize", "verify your account", "account is locked", "wire transfer",
            "verify your identity", "claim your reward");
    private static final Map<String, String> TOPIC_DISPLAY_NAMES = Map.of(
            "phishing", "Phishing & Credential Alert",
            "lottery", "Lottery / Sweepstakes Scam",
            "marketing", "Commercial Marketing / Promo",
            "crypto_scam", "Crypto / Get-Rich-Quick Scam",
            "work", "Work & Team Communication",
            "personal", "Personal & Social Email",
            "notification", "Order & Transaction Alert",
            "newsletter", "Newsletter & Educational"
    );

    private final NaiveBayesClassifierService spamClassifier;
    private final TopicClassificationService topicClassifier;
    private final TextPreprocessingService preprocessing;
    private final ModelTrainingService modelTrainingService;

    public EmailClassificationService(NaiveBayesClassifierService spamClassifier,
                                      TopicClassificationService topicClassifier,
                                      TextPreprocessingService preprocessing,
                                      ModelTrainingService modelTrainingService) {
        this.spamClassifier = spamClassifier;
        this.topicClassifier = topicClassifier;
        this.preprocessing = preprocessing;
        this.modelTrainingService = modelTrainingService;
    }

    public ClassificationResponse classifyEmail(String rawText) {
        String text = sanitize(rawText);
        if (text.isBlank()) {
            return new ClassificationResponse("ham", false, 0.5, 50.0, 0.5, 0.5,
                    "unknown", "Unknown", 0.0,
                    new NaiveBayesClassifierService.Explanation(java.util.List.of(), java.util.List.of(), 0),
                    Map.of());
        }

        NaiveBayesClassifierService.Prediction spamPrediction = spamClassifier.predict(text);
        ExtractedFeatures features = preprocessing.extractFeatures(text);
        double spamProbability = spamPrediction.spamProbability();
        spamProbability = resistBenignPadding(text, spamProbability);
        if (features.getSuspiciousUnicodeUrlCount() > 0) {
            spamProbability = Math.max(spamProbability, SUSPICIOUS_URL_SPAM_FLOOR);
        }
        double hamProbability = 1.0 - spamProbability;
        boolean isSpam = spamProbability >= 0.5;
        double confidence = isSpam ? spamProbability : hamProbability;
        TopicClassificationService.TopicPrediction topicPrediction = topicClassifier.predict(text);
        String topicDisplay = TOPIC_DISPLAY_NAMES.getOrDefault(topicPrediction.topic(),
                topicPrediction.topic().replace('_', ' ').toLowerCase(Locale.ROOT));

        return new ClassificationResponse(isSpam ? "spam" : "ham", isSpam, round4(confidence),
                round2(confidence * 100), round4(spamProbability),
                round4(hamProbability), topicPrediction.topic(), topicDisplay,
                round2(topicPrediction.confidence() * 100), spamPrediction.explanation(),
                heuristicFeatures(features));
    }

    public Map<String, Object> getModelStatistics() {
        Map<String, Object> result = new LinkedHashMap<>(spamClassifier.getModelStatistics());
        result.put("topicVocabularySize", topicClassifier.getVocabularySize());
        result.put("topicCount", topicClassifier.getClasses().size());
        return result;
    }

    public Map<String, Object> getEvaluationMetrics() {
        return modelTrainingService.getEvaluation();
    }

    private Map<String, Object> heuristicFeatures(ExtractedFeatures features) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("cap_ratio", round4(features.getCapitalLetterRatio()));
        values.put("exclamation_ratio", round4(features.getExclamationRatio()));
        values.put("question_count", features.getQuestionCount());
        values.put("currency_count", features.getCurrencyCount());
        values.put("url_count", features.getUrlCount());
        values.put("suspicious_unicode_url_count", features.getSuspiciousUnicodeUrlCount());
        values.put("digit_ratio", round4(features.getDigitRatio()));
        values.put("norm_word_count", round4(features.getNormalizedWordCount()));
        values.put("all_caps_ratio", round4(features.getAllCapsWordRatio()));
        return values;
    }

    private double resistBenignPadding(String text, double originalSpamProbability) {
        if (text.length() < MIN_LONG_MESSAGE_LENGTH || originalSpamProbability >= 0.5) {
            return originalSpamProbability;
        }

        int lastStart = Math.max(0, text.length() - SEGMENT_LENGTH);
        int[] segmentStarts = {0, Math.max(0, (text.length() - SEGMENT_LENGTH) / 2), lastStart};
        for (int segmentStart : segmentStarts) {
            int segmentEnd = Math.min(text.length(), segmentStart + SEGMENT_LENGTH);
            String segment = text.substring(segmentStart, segmentEnd);
            String normalizedSegment = segment.toLowerCase(Locale.ROOT);
            boolean containsHighRiskPhrase = HIGH_RISK_SEGMENT_PHRASES.stream().anyMatch(normalizedSegment::contains);
            if (containsHighRiskPhrase ||
                    spamClassifier.predict(segment).spamProbability() >= HIGH_SEGMENT_SPAM_THRESHOLD) {
                return Math.max(originalSpamProbability, SUSPICIOUS_URL_SPAM_FLOOR);
            }
        }
        return originalSpamProbability;
    }

    private String sanitize(String text) {
        return text == null ? "" : text.replace("\u0000", "");
    }

    private double round4(double value) { return Math.round(value * 10_000.0) / 10_000.0; }
    private double round2(double value) { return Math.round(value * 100.0) / 100.0; }

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
            @JsonProperty("explanation") NaiveBayesClassifierService.Explanation explanation,
            @JsonProperty("heuristics") Map<String, Object> heuristics) { }
}
