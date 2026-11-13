package com.classifier.spam.service.emailclassification;

import com.classifier.spam.service.textprocessing.TextPreprocessingService;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Multinomial Naive Bayes over normalized sublinear TF-IDF features. */
@Service
public class NaiveBayesClassifierService {
    private static final double ALPHA = 0.5;
    private final TextPreprocessingService preprocessing;
    private TfidfVectorizer vectorizer;
    private List<String> featureNames = List.of();
    private double[] logPriors = new double[2];
    private double[][] logFeatureProbabilities = new double[2][0];
    private int hamCount;
    private int spamCount;
    private int hamTokenCount;
    private int spamTokenCount;

    public NaiveBayesClassifierService(TextPreprocessingService preprocessing) {
        this.preprocessing = preprocessing;
    }

    public synchronized void train(List<EmailTrainingExample> examples) {
        List<String> documents = examples.stream().map(EmailTrainingExample::content).toList();
        TfidfVectorizer fittedVectorizer = new TfidfVectorizer(preprocessing, 3000, 2);
        fittedVectorizer.fit(documents);
        if (fittedVectorizer.getVocabularySize() == 0) {
            throw new IllegalStateException("Spam model vocabulary is empty after preprocessing");
        }

        double[][] featureCounts = new double[2][fittedVectorizer.getVocabularySize()];
        double[] classFeatureTotals = new double[2];
        int[] classDocumentCounts = new int[2];
        int[] classTokenTotals = new int[2];
        for (EmailTrainingExample example : examples) {
            int label = "spam".equals(example.label()) ? 1 : 0;
            classDocumentCounts[label]++;
            int tokenCount = preprocessing.tokenizeAndStem(example.content()).size();
            classTokenTotals[label] += tokenCount;
            for (Map.Entry<Integer, Double> feature : fittedVectorizer.transform(example.content()).entrySet()) {
                featureCounts[label][feature.getKey()] += feature.getValue();
                classFeatureTotals[label] += feature.getValue();
            }
        }
        int totalDocuments = examples.size();
        this.logPriors = new double[] {
                Math.log((double) classDocumentCounts[0] / totalDocuments),
                Math.log((double) classDocumentCounts[1] / totalDocuments)
        };
        this.logFeatureProbabilities = new double[2][fittedVectorizer.getVocabularySize()];
        for (int label = 0; label < 2; label++) {
            double denominator = classFeatureTotals[label] + ALPHA * fittedVectorizer.getVocabularySize();
            for (int feature = 0; feature < fittedVectorizer.getVocabularySize(); feature++) {
                logFeatureProbabilities[label][feature] = Math.log((featureCounts[label][feature] + ALPHA) / denominator);
            }
        }
        this.hamCount = classDocumentCounts[0];
        this.spamCount = classDocumentCounts[1];
        this.hamTokenCount = classTokenTotals[0];
        this.spamTokenCount = classTokenTotals[1];
        this.vectorizer = fittedVectorizer;
        this.featureNames = fittedVectorizer.getFeatureNames();
    }

    public Prediction predict(String rawText) {
        ensureTrained();
        Map<Integer, Double> features = vectorizer.transform(rawText);
        double hamLogScore = logPriors[0];
        double spamLogScore = logPriors[1];
        for (Map.Entry<Integer, Double> feature : features.entrySet()) {
            hamLogScore += feature.getValue() * logFeatureProbabilities[0][feature.getKey()];
            spamLogScore += feature.getValue() * logFeatureProbabilities[1][feature.getKey()];
        }
        double max = Math.max(hamLogScore, spamLogScore);
        double hamExp = Math.exp(hamLogScore - max);
        double spamExp = Math.exp(spamLogScore - max);
        double total = hamExp + spamExp;
        return new Prediction(spamExp / total, hamExp / total, explain(features), features.size());
    }

    private Explanation explain(Map<Integer, Double> features) {
        List<WordImpact> impacts = new ArrayList<>(features.size());
        for (Map.Entry<Integer, Double> feature : features.entrySet()) {
            int index = feature.getKey();
            double impact = feature.getValue() * (logFeatureProbabilities[1][index] - logFeatureProbabilities[0][index]);
            if (impact != 0.0) {
                impacts.add(new WordImpact(featureNames.get(index), round4(impact), round4(feature.getValue())));
            }
        }
        List<WordImpact> spamWords = impacts.stream().filter(item -> item.impact() > 0)
                .sorted(Comparator.comparingDouble(WordImpact::impact).reversed()).limit(6).toList();
        List<WordImpact> hamWords = impacts.stream().filter(item -> item.impact() < 0)
                .sorted(Comparator.comparingDouble(WordImpact::impact)).limit(6).toList();
        return new Explanation(spamWords, hamWords, features.size());
    }

    public Map<String, Object> getModelStatistics() {
        ensureTrained();
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalSamples", hamCount + spamCount);
        stats.put("totalHamSamples", hamCount);
        stats.put("totalSpamSamples", spamCount);
        stats.put("totalHamTokens", hamTokenCount);
        stats.put("totalSpamTokens", spamTokenCount);
        stats.put("vocabularySize", vectorizer.getVocabularySize());
        stats.put("algorithm", "MultinomialNB");
        stats.put("vectorizer", "TF-IDF (unigrams + bigrams, sublinear TF)");
        return stats;
    }

    public int getVocabularySize() {
        ensureTrained();
        return vectorizer.getVocabularySize();
    }

    private void ensureTrained() {
        if (vectorizer == null) throw new IllegalStateException("Spam classification model is not trained");
    }

    private static double round4(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }

    public record WordImpact(String word, double impact, double tfidf) { }
    public record Explanation(@JsonProperty("top_spam_words") List<WordImpact> topSpamWords,
                              @JsonProperty("top_ham_words") List<WordImpact> topHamWords,
                              @JsonProperty("all_analyzed_tokens") int allAnalyzedTokens) { }
    public record Prediction(double spamProbability, double hamProbability,
                             Explanation explanation, int activeFeatures) { }
}
