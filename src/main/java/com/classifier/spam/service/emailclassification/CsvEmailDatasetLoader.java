package com.classifier.spam.service.emailclassification;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PushbackReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class CsvEmailDatasetLoader {
    private final ResourceLoader resourceLoader;

    public CsvEmailDatasetLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public List<EmailTrainingExample> loadBundledDataset() {
        Resource resource = resourceLoader.getResource("classpath:dataset/emails.csv");
        if (!resource.exists()) {
            throw new IllegalStateException("Required email training dataset is missing");
        }

        try (PushbackReader reader = new PushbackReader(new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)), 1)) {
            List<List<String>> rows = readCsv(reader);
            if (rows.size() < 2) {
                throw new IllegalStateException("Email training dataset has no records");
            }

            Map<String, Integer> columns = new HashMap<>();
            List<String> header = rows.getFirst();
            for (int i = 0; i < header.size(); i++) {
                columns.put(header.get(i).strip().toLowerCase(Locale.ROOT), i);
            }
            int labelColumn = requiredColumn(columns, "label");
            int topicColumn = requiredColumn(columns, "topic");
            int contentColumn = requiredColumn(columns, "content");

            List<EmailTrainingExample> examples = new ArrayList<>();
            for (List<String> row : rows.subList(1, rows.size())) {
                if (row.size() <= Math.max(labelColumn, Math.max(topicColumn, contentColumn))) {
                    continue;
                }
                String label = row.get(labelColumn).strip().toLowerCase(Locale.ROOT);
                String topic = row.get(topicColumn).strip().toLowerCase(Locale.ROOT);
                String content = row.get(contentColumn);
                if ((label.equals("spam") || label.equals("ham")) && !topic.isBlank() && !content.isBlank()) {
                    examples.add(new EmailTrainingExample(label, topic, content));
                }
            }
            if (examples.isEmpty()) {
                throw new IllegalStateException("Email training dataset has no usable records");
            }
            return List.copyOf(examples);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read the email training dataset", exception);
        }
    }

    private int requiredColumn(Map<String, Integer> columns, String name) {
        Integer index = columns.get(name);
        if (index == null) {
            throw new IllegalStateException("Email training dataset is missing the '" + name + "' column");
        }
        return index;
    }

    private List<List<String>> readCsv(PushbackReader reader) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        int value;

        while ((value = reader.read()) != -1) {
            char current = (char) value;
            if (current == '"') {
                if (inQuotes) {
                    int next = reader.read();
                    if (next == '"') {
                        field.append('"');
                    } else {
                        inQuotes = false;
                        if (next != -1) reader.unread(next);
                    }
                } else if (field.isEmpty()) {
                    inQuotes = true;
                } else {
                    field.append(current);
                }
            } else if (current == ',' && !inQuotes) {
                row.add(field.toString());
                field.setLength(0);
            } else if ((current == '\n' || current == '\r') && !inQuotes) {
                if (current == '\r') {
                    int next = reader.read();
                    if (next != '\n' && next != -1) reader.unread(next);
                }
                row.add(field.toString());
                field.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
            } else {
                field.append(current);
            }
        }

        if (inQuotes) {
            throw new IOException("Unclosed quoted CSV field");
        }
        if (!row.isEmpty() || !field.isEmpty()) {
            row.add(field.toString());
            rows.add(row);
        }
        return rows;
    }
}
