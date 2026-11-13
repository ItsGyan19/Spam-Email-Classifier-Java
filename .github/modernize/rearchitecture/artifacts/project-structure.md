# Project Structure

- **Project type:** Java backend application. It packages as a Spring Boot executable JAR. Source currently contains no controller, route, template, or test classes.
- **Entry point:** `com.classifier.spam.SpamEmailClassifierApplication` starts Spring Boot and component-scans the `com.classifier.spam` package.
- **Layers and source counts (6 Java files):**
  - Application bootstrap: 1 (`com.classifier.spam`)
  - ML services: 2 (`service.ml`)
  - NLP services: 2 (`service.nlp`)
  - Heuristic analysis: 1 (`service.heuristic`)
- **Functional areas:**
  - Startup/model loading: reads the packaged training CSV and trains the classifier at initialization.
  - Text preprocessing: extracts structural signals, removes stop words, and applies Porter stemming.
  - Classification: maintains Naive Bayes token/sample counts and returns spam/ham probabilities with keyword impact.
  - Heuristic analysis: evaluates message content and sender against spam indicators and returns a bounded risk score.
- **Resources:** `src/main/resources/dataset/spam_training_dataset.csv`.
- **Persistence:** Spring Data JPA and H2 are dependencies, but no entity, repository, datasource configuration, or persistence use was found in the source tree.

