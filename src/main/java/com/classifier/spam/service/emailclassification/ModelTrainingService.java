package com.classifier.spam.service.emailclassification;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

@Service
public class ModelTrainingService {
    private static final Logger log = LoggerFactory.getLogger(ModelTrainingService.class);
    private final CsvEmailDatasetLoader datasetLoader;
    private final NaiveBayesClassifierService spamClassifier;
    private final TopicClassificationService topicClassifier;
    private Map<String, Object> evaluation = Map.of();

    public ModelTrainingService(CsvEmailDatasetLoader datasetLoader,
                                NaiveBayesClassifierService spamClassifier,
                                TopicClassificationService topicClassifier) {
        this.datasetLoader = datasetLoader;
        this.spamClassifier = spamClassifier;
        this.topicClassifier = topicClassifier;
    }

    @PostConstruct
    public void trainModels() {
        List<EmailTrainingExample> examples = datasetLoader.loadBundledDataset();
        Split split = stratifiedSplit(examples, 42L);
        spamClassifier.train(split.training());
        topicClassifier.train(split.training());
        this.evaluation = evaluate(examples.size(), split);
        log.info("Trained Java TF-IDF models on {} emails ({} train, {} test); vocabularies: spam={}, topic={}",
                examples.size(), split.training().size(), split.test().size(),
                spamClassifier.getVocabularySize(), topicClassifier.getVocabularySize());
    }

    public Map<String, Object> getEvaluation() {
        if (evaluation.isEmpty()) throw new IllegalStateException("Models have not been trained");
        return evaluation;
    }

    private Map<String, Object> evaluate(int datasetSize, Split split) {
        int trueNegative = 0;
        int falsePositive = 0;
        int falseNegative = 0;
        int truePositive = 0;
        int topicCorrect = 0;
        List<ScoreLabel> scores = new ArrayList<>();

        for (EmailTrainingExample example : split.test()) {
            NaiveBayesClassifierService.Prediction prediction = spamClassifier.predict(example.content());
            boolean expectedSpam = "spam".equals(example.label());
            boolean predictedSpam = prediction.spamProbability() >= 0.5;
            if (expectedSpam && predictedSpam) truePositive++;
            else if (expectedSpam) falseNegative++;
            else if (predictedSpam) falsePositive++;
            else trueNegative++;
            scores.add(new ScoreLabel(prediction.spamProbability(), expectedSpam));
            if (example.topic().equals(topicClassifier.predict(example.content()).topic())) topicCorrect++;
        }

        int testSize = split.test().size();
        double accuracy = ratio(truePositive + trueNegative, testSize);
        double precision = ratio(truePositive, truePositive + falsePositive);
        double recall = ratio(truePositive, truePositive + falseNegative);
        double f1 = precision + recall == 0.0 ? 0.0 : 2.0 * precision * recall / (precision + recall);
        double topicAccuracy = ratio(topicCorrect, testSize);

        Map<String, Object> testMetrics = new LinkedHashMap<>();
        testMetrics.put("accuracy", accuracy);
        testMetrics.put("precision", precision);
        testMetrics.put("recall", recall);
        testMetrics.put("f1", f1);
        testMetrics.put("roc_auc", rocAuc(scores));

        Map<String, Object> confusionMatrix = new LinkedHashMap<>();
        confusionMatrix.put("tn", trueNegative);
        confusionMatrix.put("fp", falsePositive);
        confusionMatrix.put("fn", falseNegative);
        confusionMatrix.put("tp", truePositive);

        Map<String, Object> candidateMetrics = new LinkedHashMap<>();
        candidateMetrics.put("accuracy_mean", accuracy);
        candidateMetrics.put("precision_mean", precision);
        candidateMetrics.put("recall_mean", recall);
        candidateMetrics.put("f1_mean", f1);
        candidateMetrics.put("roc_auc_mean", testMetrics.get("roc_auc"));

        Map<String, Object> benchmarks = new LinkedHashMap<>();
        benchmarks.put("MultinomialNB", candidateMetrics);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("best_algorithm", "MultinomialNB");
        result.put("evaluation_method", "stratified 80/20 holdout (seed 42)");
        result.put("dataset_size", datasetSize);
        result.put("train_size", split.training().size());
        result.put("test_size", testSize);
        result.put("test_metrics", testMetrics);
        result.put("confusion_matrix", confusionMatrix);
        result.put("topic_test_accuracy", topicAccuracy);
        result.put("benchmark_results", benchmarks);
        result.put("topic_classes", topicClassifier.getClasses());
        return Map.copyOf(result);
    }

    private Split stratifiedSplit(List<EmailTrainingExample> examples, long seed) {
        Map<String, List<EmailTrainingExample>> byLabel = new TreeMap<>();
        for (EmailTrainingExample example : examples) {
            byLabel.computeIfAbsent(example.label(), ignored -> new ArrayList<>()).add(example);
        }
        List<EmailTrainingExample> training = new ArrayList<>();
        List<EmailTrainingExample> test = new ArrayList<>();
        Random random = new Random(seed);
        for (List<EmailTrainingExample> classExamples : byLabel.values()) {
            Collections.shuffle(classExamples, random);
            int testCount = (int) Math.ceil(classExamples.size() * 0.2);
            test.addAll(classExamples.subList(0, testCount));
            training.addAll(classExamples.subList(testCount, classExamples.size()));
        }
        return new Split(List.copyOf(training), List.copyOf(test));
    }

    private double rocAuc(List<ScoreLabel> scores) {
        long positiveCount = scores.stream().filter(ScoreLabel::positive).count();
        long negativeCount = scores.size() - positiveCount;
        if (positiveCount == 0 || negativeCount == 0) return 0.0;
        List<ScoreLabel> ordered = scores.stream().sorted(Comparator.comparingDouble(ScoreLabel::score)).toList();
        double positiveRankSum = 0.0;
        int index = 0;
        while (index < ordered.size()) {
            int end = index + 1;
            while (end < ordered.size() && ordered.get(end).score() == ordered.get(index).score()) end++;
            double averageRank = (index + 1 + end) / 2.0;
            for (int i = index; i < end; i++) {
                if (ordered.get(i).positive()) positiveRankSum += averageRank;
            }
            index = end;
        }
        return (positiveRankSum - positiveCount * (positiveCount + 1) / 2.0) / (positiveCount * negativeCount);
    }

    private double ratio(double numerator, double denominator) {
        return denominator == 0.0 ? 0.0 : numerator / denominator;
    }

    private record Split(List<EmailTrainingExample> training, List<EmailTrainingExample> test) { }
    private record ScoreLabel(double score, boolean positive) { }
}
