package com.classifier.spam.service.emailclassification;

import com.classifier.spam.service.textprocessing.TextPreprocessingService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** Multiclass softmax model for the eight email topic labels. */
@Service
public class TopicClassificationService {
    private static final double C = 3.0;
    private static final int TRAINING_EPOCHS = 160;
    private final TextPreprocessingService preprocessing;
    private TfidfVectorizer vectorizer;
    private List<String> classes = List.of();
    private double[][] weights;
    private double[] biases;
    private int trainingSamples;

    public TopicClassificationService(TextPreprocessingService preprocessing) {
        this.preprocessing = preprocessing;
    }

    public synchronized void train(List<EmailTrainingExample> examples) {
        List<String> documents = examples.stream().map(EmailTrainingExample::content).toList();
        TfidfVectorizer fittedVectorizer = new TfidfVectorizer(preprocessing, 2500, 2);
        fittedVectorizer.fit(documents);
        List<String> labels = new TreeSet<>(examples.stream().map(EmailTrainingExample::topic).toList()).stream().toList();
        Map<String, Integer> labelIndexes = new HashMap<>();
        for (int i = 0; i < labels.size(); i++) labelIndexes.put(labels.get(i), i);

        List<Map<Integer, Double>> vectors = new ArrayList<>(examples.size());
        int[] targets = new int[examples.size()];
        for (int i = 0; i < examples.size(); i++) {
            EmailTrainingExample example = examples.get(i);
            vectors.add(fittedVectorizer.transform(example.content()));
            targets[i] = labelIndexes.get(example.topic());
        }

        int classesCount = labels.size();
        int featuresCount = fittedVectorizer.getVocabularySize();
        double[][] modelWeights = new double[classesCount][featuresCount];
        double[] modelBiases = new double[classesCount];
        double regularization = 1.0 / (C * examples.size());
        for (int epoch = 0; epoch < TRAINING_EPOCHS; epoch++) {
            double[][] weightGradients = new double[classesCount][featuresCount];
            double[] biasGradients = new double[classesCount];

            for (int row = 0; row < vectors.size(); row++) {
                double[] probabilities = probabilities(vectors.get(row), modelWeights, modelBiases);
                for (int classIndex = 0; classIndex < classesCount; classIndex++) {
                    double error = probabilities[classIndex] - (targets[row] == classIndex ? 1.0 : 0.0);
                    biasGradients[classIndex] += error;
                    for (Map.Entry<Integer, Double> feature : vectors.get(row).entrySet()) {
                        weightGradients[classIndex][feature.getKey()] += error * feature.getValue();
                    }
                }
            }

            double learningRate = 1.5 / (1.0 + epoch * 0.025);
            for (int classIndex = 0; classIndex < classesCount; classIndex++) {
                modelBiases[classIndex] -= learningRate * biasGradients[classIndex] / examples.size();
                for (int featureIndex = 0; featureIndex < featuresCount; featureIndex++) {
                    double gradient = weightGradients[classIndex][featureIndex] / examples.size()
                            + regularization * modelWeights[classIndex][featureIndex];
                    modelWeights[classIndex][featureIndex] -= learningRate * gradient;
                }
            }
        }

        this.vectorizer = fittedVectorizer;
        this.classes = List.copyOf(labels);
        this.weights = modelWeights;
        this.biases = modelBiases;
        this.trainingSamples = examples.size();
    }

    public TopicPrediction predict(String rawText) {
        ensureTrained();
        Map<Integer, Double> features = vectorizer.transform(rawText);
        double[] probabilities = probabilities(features, weights, biases);
        int bestIndex = 0;
        for (int i = 1; i < probabilities.length; i++) {
            if (probabilities[i] > probabilities[bestIndex]) bestIndex = i;
        }
        return new TopicPrediction(classes.get(bestIndex), probabilities[bestIndex]);
    }

    public int getVocabularySize() {
        ensureTrained();
        return vectorizer.getVocabularySize();
    }

    public int getTrainingSamples() { return trainingSamples; }
    public List<String> getClasses() { return classes; }

    private double[] probabilities(Map<Integer, Double> features, double[][] modelWeights, double[] modelBiases) {
        double[] logits = modelBiases.clone();
        for (Map.Entry<Integer, Double> feature : features.entrySet()) {
            for (int classIndex = 0; classIndex < logits.length; classIndex++) {
                logits[classIndex] += modelWeights[classIndex][feature.getKey()] * feature.getValue();
            }
        }
        double max = Double.NEGATIVE_INFINITY;
        for (double logit : logits) max = Math.max(max, logit);
        double sum = 0.0;
        for (int i = 0; i < logits.length; i++) {
            logits[i] = Math.exp(logits[i] - max);
            sum += logits[i];
        }
        for (int i = 0; i < logits.length; i++) logits[i] /= sum;
        return logits;
    }

    private void ensureTrained() {
        if (vectorizer == null) throw new IllegalStateException("Topic classification model is not trained");
    }

    public record TopicPrediction(String topic, double confidence) { }
}
