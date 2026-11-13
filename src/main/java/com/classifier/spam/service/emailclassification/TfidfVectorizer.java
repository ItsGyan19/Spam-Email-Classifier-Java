package com.classifier.spam.service.emailclassification;

import com.classifier.spam.service.textprocessing.TextPreprocessingService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** TF-IDF word and bigram vectorizer. */
public final class TfidfVectorizer {
    private final TextPreprocessingService preprocessing;
    private final int maxFeatures;
    private final int minDocumentFrequency;
    private Map<String, Integer> featureIndexes = Map.of();
    private double[] inverseDocumentFrequencies = new double[0];

    public TfidfVectorizer(TextPreprocessingService preprocessing, int maxFeatures, int minDocumentFrequency) {
        this.preprocessing = preprocessing;
        this.maxFeatures = maxFeatures;
        this.minDocumentFrequency = minDocumentFrequency;
    }

    public void fit(List<String> documents) {
        Map<String, Integer> documentFrequency = new HashMap<>();
        Map<String, Integer> termFrequency = new HashMap<>();
        for (String document : documents) {
            List<String> terms = terms(preprocessing.tokenizeAndStem(document));
            Set<String> uniqueTerms = new HashSet<>(terms);
            for (String term : uniqueTerms) documentFrequency.merge(term, 1, Integer::sum);
            for (String term : terms) termFrequency.merge(term, 1, Integer::sum);
        }

        List<String> selected = documentFrequency.entrySet().stream()
                .filter(entry -> entry.getValue() >= minDocumentFrequency)
                .sorted(Comparator.<Map.Entry<String, Integer>>comparingInt(entry -> termFrequency.get(entry.getKey()))
                        .reversed().thenComparing(Map.Entry::getKey))
                .limit(maxFeatures)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();

        Map<String, Integer> indexes = new HashMap<>(selected.size());
        double[] idf = new double[selected.size()];
        for (int i = 0; i < selected.size(); i++) {
            String term = selected.get(i);
            indexes.put(term, i);
            idf[i] = Math.log((1.0 + documents.size()) / (1.0 + documentFrequency.get(term))) + 1.0;
        }
        this.featureIndexes = Map.copyOf(indexes);
        this.inverseDocumentFrequencies = idf;
    }

    public Map<Integer, Double> transform(String document) {
        if (featureIndexes.isEmpty()) return Map.of();
        List<String> terms = terms(preprocessing.tokenizeAndStem(document));
        Map<Integer, Integer> counts = new HashMap<>();
        for (String term : terms) {
            Integer index = featureIndexes.get(term);
            if (index != null) counts.merge(index, 1, Integer::sum);
        }
        Map<Integer, Double> weights = new HashMap<>(counts.size());
        double normSquared = 0.0;
        for (Map.Entry<Integer, Integer> entry : counts.entrySet()) {
            double tf = 1.0 + Math.log(entry.getValue());
            double weight = tf * inverseDocumentFrequencies[entry.getKey()];
            weights.put(entry.getKey(), weight);
            normSquared += weight * weight;
        }
        if (normSquared == 0.0) return Map.of();
        double norm = Math.sqrt(normSquared);
        weights.replaceAll((index, weight) -> weight / norm);
        return weights;
    }

    public int getVocabularySize() {
        return featureIndexes.size();
    }

    public List<String> getFeatureNames() {
        return featureIndexes.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .toList();
    }

    private List<String> terms(List<String> tokens) {
        List<String> terms = new ArrayList<>(Math.max(0, tokens.size() * 2 - 1));
        for (int i = 0; i < tokens.size(); i++) {
            terms.add(tokens.get(i));
            if (i + 1 < tokens.size()) terms.add(tokens.get(i) + " " + tokens.get(i + 1));
        }
        return terms;
    }
}
