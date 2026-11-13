package com.classifier.spam.cli;

import com.classifier.spam.SpamEmailClassifierApplication;
import com.classifier.spam.service.emailclassification.EmailClassificationService;
import com.classifier.spam.service.emailclassification.EmailClassificationService.ClassificationResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.Collectors;

/**
 * Command-line interface for the Spam Email Classifier.
 * Supports single email prediction, sample suite benchmark,
 * holdout model metrics, and an interactive REPL.
 */
public final class SpamClassifierCli {

    private static final Map<String, String> SAMPLES = new LinkedHashMap<>();

    static {
        SAMPLES.put("Phishing Alert",
                "URGENT: Your PayPal account has been temporarily restricted due to suspicious login attempts. " +
                        "Verify your identity immediately at http://paypal-verify-account-security.com or account will be suspended.");
        SAMPLES.put("$2.5M Lottery Prize",
                "CONGRATULATIONS! You have been selected as the grand winner of $2,500,000 in the International Lottery! " +
                        "Wire transfer is ready. Reply with your bank details, full name, and phone number to release funds.");
        SAMPLES.put("Crypto Trading Bot",
                "EARN $1,500 TO $3,000 PER DAY FROM HOME! Our Automated Bitcoin AI Trading Bot guarantees 450% weekly returns! " +
                        "Claim your free registration bonus today at http://btc-wealth-robot.biz");
        SAMPLES.put("Team Sprint Planning",
                "Hi team, please find attached the weekly sprint report before our engineering sync tomorrow at 2 PM. " +
                        "Please update your Jira tickets with remaining story points. Thanks!");
        SAMPLES.put("Flight Booking Confirmation",
                "Your flight reservation for flight UA 492 to San Francisco (SFO) is confirmed for Nov 14. " +
                        "E-ticket and baggage receipts are attached. Thank you for flying United Airlines.");
    }

    private SpamClassifierCli() { }

    public static void main(String[] args) throws Exception {
        if (args.length == 0 || "--help".equals(args[0]) || "-h".equals(args[0])) {
            printUsage();
            return;
        }

        boolean jsonOutput = hasJsonFlag(args);

        System.setProperty("logging.level.root", "WARN");
        System.setProperty("logging.level.org.springframework", "WARN");
        System.setProperty("logging.level.org.springframework.boot", "WARN");

        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(SpamEmailClassifierApplication.class)
                .web(WebApplicationType.NONE)
                .bannerMode(org.springframework.boot.Banner.Mode.OFF)
                .logStartupInfo(false)
                .properties(
                        "logging.level.root=WARN",
                        "logging.level.org.springframework=WARN",
                        "logging.level.org.springframework.boot=WARN",
                        "logging.level.com.classifier=WARN",
                        "spring.main.banner-mode=off"
                )
                .run()) {

            EmailClassificationService service = context.getBean(EmailClassificationService.class);
            ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

            switch (args[0]) {
                case "predict" -> {
                    String text = readTextArgument(args);
                    ClassificationResponse response = service.classifyEmail(text);
                    if (jsonOutput) {
                        System.out.println(mapper.writeValueAsString(response));
                    } else {
                        printResultCard("Manual Input", text, response);
                    }
                }
                case "test-samples" -> runSamples(service, mapper, jsonOutput);
                case "metrics" -> {
                    Map<String, Object> metrics = service.getEvaluationMetrics();
                    if (jsonOutput) {
                        System.out.println(mapper.writeValueAsString(metrics));
                    } else {
                        printMetricsCard(metrics);
                    }
                }
                case "interactive" -> runInteractive(service, mapper, jsonOutput);
                default -> printUsage();
            }
        }
    }

    private static boolean hasJsonFlag(String[] args) {
        for (String arg : args) {
            if ("--json".equalsIgnoreCase(arg) || "-j".equalsIgnoreCase(arg)) {
                return true;
            }
        }
        return false;
    }

    private static String readTextArgument(String[] args) {
        StringBuilder sb = new StringBuilder();
        boolean collecting = false;
        for (int i = 1; i < args.length; i++) {
            if (args[i].startsWith("--text=")) {
                return args[i].substring("--text=".length());
            }
            if ("--text".equals(args[i])) {
                collecting = true;
                continue;
            }
            if (collecting) {
                if (args[i].startsWith("--")) {
                    break;
                }
                if (!sb.isEmpty()) sb.append(" ");
                sb.append(args[i]);
            }
        }
        if (!sb.isEmpty()) {
            return sb.toString();
        }
        throw new IllegalArgumentException("Missing text. Use: predict --text \"your email content here\"");
    }

    private static void runSamples(EmailClassificationService service, ObjectMapper mapper, boolean jsonOutput) throws Exception {
        if (jsonOutput) {
            Map<String, Object> results = new LinkedHashMap<>();
            for (Map.Entry<String, String> sample : SAMPLES.entrySet()) {
                results.put(sample.getKey(), service.classifyEmail(sample.getValue()));
            }
            System.out.println(mapper.writeValueAsString(results));
        } else {
            System.out.println("\nRUNNING PRE-DEFINED BENCHMARK SAMPLES:\n");
            for (Map.Entry<String, String> sample : SAMPLES.entrySet()) {
                ClassificationResponse res = service.classifyEmail(sample.getValue());
                printResultCard(sample.getKey(), sample.getValue(), res);
            }
        }
    }

    private static void runInteractive(EmailClassificationService service, ObjectMapper mapper, boolean jsonOutput) throws Exception {
        System.out.println("=".repeat(80));
        System.out.println("SPAM EMAIL CLASSIFIER - INTERACTIVE REPL");
        System.out.println("Type or paste an email message below, then press Enter.");
        System.out.println("Type 'exit' or 'quit' to terminate the session.");
        System.out.println("=".repeat(80));

        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.print("\nEnter Email Text > ");
                if (!scanner.hasNextLine()) break;
                String text = scanner.nextLine();
                if (text == null) break;
                String trimmed = text.strip();
                if ("exit".equalsIgnoreCase(trimmed) || "quit".equalsIgnoreCase(trimmed) || "q".equalsIgnoreCase(trimmed)) {
                    System.out.println("Exiting interactive mode. Goodbye!");
                    break;
                }
                if (trimmed.isEmpty()) continue;

                ClassificationResponse response = service.classifyEmail(trimmed);
                if (jsonOutput) {
                    System.out.println(mapper.writeValueAsString(response));
                } else {
                    printResultCard("Live Interactive Test", trimmed, response);
                }
            }
        }
    }

    private static void printResultCard(String title, String text, ClassificationResponse res) {
        System.out.println("=".repeat(80));
        if (title != null && !title.isBlank()) {
            System.out.println("EMAIL SAMPLE: " + title);
            System.out.println("-".repeat(80));
        }
        System.out.println("CONTENT:");
        String preview = text.strip().length() < 220 ? text.strip() : text.strip().substring(0, 220) + "...";
        System.out.println("  \"" + preview + "\"\n");

        String tag = res.isSpam() ? "[ SPAM ]" : "[ HAM (Legitimate) ]";
        System.out.printf(Locale.ROOT, "PREDICTION:       %-22s (Confidence: %.2f%%)%n", tag, res.confidencePercent());
        System.out.printf(Locale.ROOT, "PREDICTED TOPIC:  %-22s (Confidence: %.2f%%)%n", res.topicDisplay(), res.topicConfidence());
        System.out.printf(Locale.ROOT, "SPAM PROBABILITY: %.2f%% | HAM PROBABILITY: %.2f%%%n", res.spamProbability() * 100, res.hamProbability() * 100);

        System.out.println("\nSTRUCTURAL SIGNALS:");
        Map<String, Object> h = res.heuristics();
        if (h != null) {
            Number capRatio = (Number) h.getOrDefault("cap_ratio", 0.0);
            Number currency = (Number) h.getOrDefault("currency_count", 0);
            Number urls = (Number) h.getOrDefault("url_count", 0);
            Number exclamation = (Number) h.getOrDefault("exclamation_ratio", 0.0);
            System.out.printf(Locale.ROOT, "  • Capital Letter Ratio: %.1f%%%n", capRatio.doubleValue() * 100);
            System.out.printf(Locale.ROOT, "  • Currency Symbols:     %d%n", currency.intValue());
            System.out.printf(Locale.ROOT, "  • URL / Link Count:     %d%n", urls.intValue());
            System.out.printf(Locale.ROOT, "  • Exclamation Marks:    %.1f%% frequency%n", exclamation.doubleValue() * 100);
        }

        if (res.explanation() != null) {
            if (res.isSpam() && res.explanation().topSpamWords() != null && !res.explanation().topSpamWords().isEmpty()) {
                String cues = res.explanation().topSpamWords().stream().limit(4)
                        .map(item -> String.format(Locale.ROOT, "'%s' (+%.2f)", item.word(), item.impact()))
                        .collect(Collectors.joining(", "));
                System.out.println("\nTOP SPAM SIGNALS DETECTED:\n  " + cues);
            } else if (!res.isSpam() && res.explanation().topHamWords() != null && !res.explanation().topHamWords().isEmpty()) {
                String cues = res.explanation().topHamWords().stream().limit(4)
                        .map(item -> String.format(Locale.ROOT, "'%s' (%.2f)", item.word(), item.impact()))
                        .collect(Collectors.joining(", "));
                System.out.println("\nTOP HAM SIGNALS DETECTED:\n  " + cues);
            }
        }
        System.out.println("=".repeat(80) + "\n");
    }

    private static void printMetricsCard(Map<String, Object> metrics) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("MODEL METRICS & BENCHMARK SUMMARY (Java TF-IDF + Naive Bayes)");
        System.out.println("=".repeat(80));
        System.out.println("Selected Best Algorithm: " + metrics.get("best_algorithm"));
        System.out.printf("Total Dataset Samples:   %s (Train: %s, Test: %s)%n",
                metrics.get("dataset_size"), metrics.get("train_size"), metrics.get("test_size"));
        System.out.println("Evaluation Method:       " + metrics.get("evaluation_method"));

        @SuppressWarnings("unchecked")
        Map<String, Object> testMetrics = (Map<String, Object>) metrics.get("test_metrics");
        if (testMetrics != null) {
            System.out.println("\nHELD-OUT TEST SET METRICS:");
            System.out.printf(Locale.ROOT, "  • Accuracy:  %.2f%%%n", ((Number) testMetrics.get("accuracy")).doubleValue() * 100);
            System.out.printf(Locale.ROOT, "  • Precision: %.2f%%%n", ((Number) testMetrics.get("precision")).doubleValue() * 100);
            System.out.printf(Locale.ROOT, "  • Recall:    %.2f%%%n", ((Number) testMetrics.get("recall")).doubleValue() * 100);
            System.out.printf(Locale.ROOT, "  • F1 Score:  %.2f%%%n", ((Number) testMetrics.get("f1")).doubleValue() * 100);
            System.out.printf(Locale.ROOT, "  • ROC-AUC:   %.4f%n", ((Number) testMetrics.get("roc_auc")).doubleValue());
        }
        if (metrics.containsKey("topic_test_accuracy")) {
            System.out.printf(Locale.ROOT, "  • Topic Accuracy: %.2f%%%n", ((Number) metrics.get("topic_test_accuracy")).doubleValue() * 100);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> cm = (Map<String, Object>) metrics.get("confusion_matrix");
        if (cm != null) {
            System.out.println("\nCONFUSION MATRIX:");
            System.out.printf("  • True Negatives (Ham):  %s%n", cm.get("tn"));
            System.out.printf("  • False Positives (Spam): %s%n", cm.get("fp"));
            System.out.printf("  • False Negatives (Ham):  %s%n", cm.get("fn"));
            System.out.printf("  • True Positives (Spam): %s%n", cm.get("tp"));
        }
        System.out.println("=".repeat(80) + "\n");
    }

    private static void printUsage() {
        System.out.println("""
                Spam Email Classifier CLI (Java Spring Boot)
                Usage:
                  predict --text "<email text>" [--json]   Classify a single email
                  test-samples [--json]                    Run built-in benchmark samples
                  metrics [--json]                         Show held-out test evaluation metrics
                  interactive [--json]                     Start interactive prompt REPL
                  --help, -h                               Show this help message
                """);
    }
}
