package com.classifier.spam.service.emailrules;

import com.classifier.spam.service.textprocessing.TextPreprocessingService.ExtractedFeatures;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class HeuristicRuleEngineService {

    public static class HeuristicTrigger {
        private final String ruleId;
        private final String ruleName;
        private final String description;
        private final double penaltyScore; // 0 to 100 contribution
        private final String severity; // LOW, MEDIUM, HIGH, CRITICAL

        public HeuristicTrigger(String ruleId, String ruleName, String description, double penaltyScore, String severity) {
            this.ruleId = ruleId;
            this.ruleName = ruleName;
            this.description = description;
            this.penaltyScore = penaltyScore;
            this.severity = severity;
        }

        public String getRuleId() { return ruleId; }
        public String getRuleName() { return ruleName; }
        public String getDescription() { return description; }
        public double getPenaltyScore() { return penaltyScore; }
        public String getSeverity() { return severity; }
    }

    public static class HeuristicAnalysisResult {
        private final List<HeuristicTrigger> triggers;
        private final double totalRiskScore; // 0.0 to 100.0

        public HeuristicAnalysisResult(List<HeuristicTrigger> triggers, double totalRiskScore) {
            this.triggers = triggers;
            this.totalRiskScore = totalRiskScore;
        }

        public List<HeuristicTrigger> getTriggers() { return triggers; }
        public double getTotalRiskScore() { return totalRiskScore; }
    }

    private static final List<String> URGENCY_PHRASES = List.of(
            "immediate response", "action required", "urgent action", "act now", "account suspended",
            "account has been locked", "within 24 hours", "final notice", "immediate action",
            "critical security", "verify immediately", "failure to respond", "limited time"
    );

    private static final List<String> FINANCIAL_SCAM_PHRASES = List.of(
            "won the lottery", "claim your prize", "million dollars", "selected as a winner",
            "inheritance fund", "beneficiary", "wire transfer", "bitcoin investment", "guaranteed returns",
            "free cash", "huge discount", "no risk guaranteed", "easy money", "unclaimed funds"
    );

    private static final List<String> PHISHING_PHRASES = List.of(
            "verify your password", "confirm your account", "update billing", "login to verify",
            "security alert: update", "compromised credentials", "click here to reset",
            "unusual login activity", "re-activate your account", "confirm identity"
    );

    private static final List<String> SUSPICIOUS_DOMAINS = List.of(
            ".xyz", ".top", ".buzz", ".work", ".click", ".surf", ".gq", ".cf", ".tk", ".ml"
    );

    private static final Pattern IP_URL_PATTERN = Pattern.compile("https?://\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}");

    public HeuristicAnalysisResult analyze(String subject, String sender, String body, ExtractedFeatures features) {
        List<HeuristicTrigger> triggers = new ArrayList<>();
        double accumulatedScore = 0.0;

        String fullText = ((subject != null ? subject : "") + " " + (body != null ? body : ""))
                .toLowerCase(Locale.ROOT);

        // Rule 1: High Caps Ratio
        if (features.getCapitalLetterRatio() > 0.40) {
            triggers.add(new HeuristicTrigger(
                    "RULE_HIGH_CAPS",
                    "Excessive Capital Letters",
                    String.format(Locale.ROOT, "%.1f%% of text is uppercase (shouting / aggressive attention seeking)", features.getCapitalLetterRatio() * 100),
                    25.0,
                    "HIGH"
            ));
            accumulatedScore += 25.0;
        } else if (features.getCapitalLetterRatio() > 0.25) {
            triggers.add(new HeuristicTrigger(
                    "RULE_MODERATE_CAPS",
                    "Elevated Capital Letters",
                    String.format(Locale.ROOT, "%.1f%% of text is uppercase", features.getCapitalLetterRatio() * 100),
                    12.0,
                    "MEDIUM"
            ));
            accumulatedScore += 12.0;
        }

        // Rule 2: Urgency Triggers
        for (String phrase : URGENCY_PHRASES) {
            if (fullText.contains(phrase)) {
                triggers.add(new HeuristicTrigger(
                        "RULE_URGENCY",
                        "High Urgency Pressure",
                        "Contains artificial urgency indicator: \"" + phrase + "\"",
                        20.0,
                        "HIGH"
                ));
                accumulatedScore += 20.0;
                break;
            }
        }

        // Rule 3: Financial Scam / Prize Winnings
        for (String phrase : FINANCIAL_SCAM_PHRASES) {
            if (fullText.contains(phrase)) {
                triggers.add(new HeuristicTrigger(
                        "RULE_FINANCIAL_SCAM",
                        "Lottery / Financial Lure",
                        "Contains prize or financial lure phrase: \"" + phrase + "\"",
                        30.0,
                        "CRITICAL"
                ));
                accumulatedScore += 30.0;
                break;
            }
        }

        // Rule 4: Credential Phishing
        for (String phrase : PHISHING_PHRASES) {
            if (fullText.contains(phrase)) {
                triggers.add(new HeuristicTrigger(
                        "RULE_PHISHING_CREDENTIALS",
                        "Credential Harvesting / Phishing",
                        "Asks for account or credential verification: \"" + phrase + "\"",
                        35.0,
                        "CRITICAL"
                ));
                accumulatedScore += 35.0;
                break;
            }
        }

        // Rule 5: IP address in link
        if (IP_URL_PATTERN.matcher(fullText).find()) {
            triggers.add(new HeuristicTrigger(
                    "RULE_IP_URL",
                    "Direct IP Link Detected",
                    "Contains direct numeric IP link instead of a verified domain",
                    25.0,
                    "HIGH"
            ));
            accumulatedScore += 25.0;
        }

        // Rule 6: Suspicious TLDs
        for (String tld : SUSPICIOUS_DOMAINS) {
            if (fullText.contains(tld)) {
                triggers.add(new HeuristicTrigger(
                        "RULE_SUSPICIOUS_TLD",
                        "Low-Reputation Domain Extension",
                        "Contains link ending in suspicious TLD: " + tld,
                        15.0,
                        "MEDIUM"
                ));
                accumulatedScore += 15.0;
                break;
            }
        }

        // Rule 7: Excessive Exclamations / Currency Symbols
        if (features.getExclamationCount() >= 4) {
            triggers.add(new HeuristicTrigger(
                    "RULE_EXCESSIVE_PUNCTUATION",
                    "Excessive Exclamation Marks",
                    "Found " + features.getExclamationCount() + " exclamation marks in content",
                    10.0,
                    "LOW"
            ));
            accumulatedScore += 10.0;
        }

        if (features.getCurrencyCount() >= 3) {
            triggers.add(new HeuristicTrigger(
                    "RULE_EXCESSIVE_CURRENCY",
                    "Multiple Monetary References",
                    "Found multiple currency symbols or references",
                    15.0,
                    "MEDIUM"
            ));
            accumulatedScore += 15.0;
        }

        // Rule 8: Suspicious Sender Domain
        if (sender != null && !sender.isBlank()) {
            String lowerSender = sender.toLowerCase(Locale.ROOT);
            if (lowerSender.contains("noreply") && (lowerSender.contains(".xyz") || lowerSender.contains(".top"))) {
                triggers.add(new HeuristicTrigger(
                        "RULE_SUSPICIOUS_SENDER",
                        "Suspicious Sender Address",
                        "Sender address has characteristics of disposable or spoofed email",
                        20.0,
                        "HIGH"
                ));
                accumulatedScore += 20.0;
            }
        }

        // Cap risk score between 0 and 100
        double totalRiskScore = Math.min(100.0, accumulatedScore);

        return new HeuristicAnalysisResult(triggers, totalRiskScore);
    }
}

