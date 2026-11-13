# 🛡️ Email Safety Checker – Spam Email Classifier

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.7-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Build](https://img.shields.io/badge/Build-Maven-blue.svg)](https://maven.apache.org/)
[![Tests](https://img.shields.io/badge/Tests-JUnit%205-red.svg)](https://junit.org/junit5/)
[![Docker](https://img.shields.io/badge/Deployment-Docker-blue.svg)](https://www.docker.com/)

A practical, clean, end-to-end Java Spring Boot application that analyzes incoming emails to detect **spam vs. legitimate (ham)** messages, predicts the **fine-grained email topic** (Phishing, Lottery, Crypto Scam, Marketing, Work, etc.), and provides **Explainable AI (XAI) feature attribution** for every prediction.

Built with a clean **Controller-Service-DTO** layered architecture, native Java NLP/ML algorithms (TF-IDF vectorization and Multinomial Naive Bayes), in-memory rate limiting, and an interactive Thymeleaf web dashboard.

---

## 📌 Table of Contents

- [Project Architecture](#-project-architecture)
- [How It Works (Interview-Ready Explanation)](#-how-it-works-interview-ready-explanation)
  - [1. Natural Language Processing (NLP) Pipeline](#1-natural-language-processing-nlp-pipeline)
  - [2. Feature Extraction: TF-IDF (Unigrams & Bigrams)](#2-feature-extraction-tf-idf-unigrams--bigrams)
  - [3. Classification Engine: Multinomial Naive Bayes](#3-classification-engine-multinomial-naive-bayes)
  - [4. Multi-Class Topic Classification](#4-multi-class-topic-classification)
  - [5. Explainability (XAI) & Domain Heuristics](#5-explainability-xai--domain-heuristics)
- [Core Features](#-core-features)
- [Technology Stack](#-technology-stack)
- [Directory Structure](#-directory-structure)
- [Getting Started](#-getting-started)
  - [Prerequisites](#prerequisites)
  - [Run Locally](#run-locally)
  - [Run with Docker](#run-with-docker)
  - [Run Unit & Integration Tests](#run-unit--integration-tests)
- [Command-Line Interface (CLI)](#-command-line-interface-cli)
- [REST API Reference](#-rest-api-reference)
- [Security & Production Safeguards](#-security--production-safeguards)
- [How to Explain This Project in an Interview](#-how-to-explain-this-project-in-an-interview)

---

## 🏗️ Project Architecture

The application strictly adheres to the standard Spring Boot **Controller-Service-DTO** layered architecture:

```
┌────────────────────────────────────────────────────────┐
│               Client Requests                          │
│     (Web Browser Dashboard / CLI / REST API Client)    │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│               Security & Filter Layer                  │
│   • SecurityHeadersFilter (CSP, X-Frame, Nosniff)      │
│   • ApiRateLimitFilter (Sliding Window: 30 req/min)    │
│   • RequestBodyLimitFilter (Max 1MB payload buffer)    │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│               Web & Controller Layer                   │
│   • ClassificationController (Thymeleaf view)          │
│   • ClassificationApiController (REST JSON endpoints)  │
│   • ApiExceptionHandler (@RestControllerAdvice)        │
└───────────────────────────┬────────────────────────────┘
                            │ DTOs (Request / Response)
                            ▼
┌────────────────────────────────────────────────────────┐
│               Service & ML Layer                       │
│   • EmailClassificationService (Orchestrator)          │
│   • TextPreprocessingService (Stemming & Cleaning)     │
│   • TfidfVectorizer (Sublinear TF, Unigrams+Bigrams)   │
│   • NaiveBayesClassifierService (Multinomial NB)       │
│   • TopicClassificationService (8-class Softmax)       │
│   • HeuristicRuleEngineService (Domain Rules)          │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│            Dataset & In-Memory Model State             │
│   • CsvEmailDatasetLoader (2,600 emails dataset)       │
│   • Deterministic 80/20 Stratified Train/Test Split    │
└────────────────────────────────────────────────────────┘
```

---

## 🧠 How It Works (Interview-Ready Explanation)

### 1. Natural Language Processing (NLP) Pipeline
Before feeding text into machine learning algorithms, raw emails undergo systematic normalization:
* **HTML Stripping**: Eliminates HTML markup tags while retaining text content.
* **Entity Tokenization**: Normalizes URLs to `token_url`, emails to `token_email`, currencies to `token_money`, phone numbers to `token_phone`, and digits to `token_num`.
* **Case Folding & Punctuation**: Converts text to lowercase and cleans non-alphanumeric punctuation.
* **Stopword Filtering**: Removes common English filler words (*the, is, at, which*) while selectively retaining critical intent words (*free, urgent, win, won, no, not*).
* **Porter Stemming**: Reduces morphological variants to base stems (e.g., *"banking"*, *"banks"*, *"banked"* $\rightarrow$ *"bank"*).

### 2. Feature Extraction: TF-IDF (Unigrams & Bigrams)
Text is represented numerically using **Term Frequency - Inverse Document Frequency (TF-IDF)**:
$$\text{TF-IDF}(t, d) = \text{TF}(t, d) \times \text{IDF}(t)$$
* **Sublinear Term Frequency Scaling**: $\text{TF}(t, d) = 1 + \ln(\text{count})$, preventing repeated keywords from dominating the score.
* **Inverse Document Frequency**: $\text{IDF}(t) = \ln\left(\frac{1 + N}{1 + \text{DF}(t)}\right) + 1$, assigning higher weights to rare, discriminative words.
* **Unigrams + Bigrams**: Captures both single words (*"account"*, *"verify"*) and essential two-word phrases (*"verify account"*, *"wire transfer"*, *"claim prize"*).
* **Vocabulary Pruning**: Applies `min_df=2` to drop hapax legomena (noise words that only appear once).

### 3. Classification Engine: Multinomial Naive Bayes
Binary classification (Spam vs. Ham) is powered by **Multinomial Naive Bayes**:
$$P(\text{Spam} \mid \text{email}) = \frac{P(\text{Spam}) \prod_{i=1}^n P(w_i \mid \text{Spam})}{P(\text{email})}$$
* **Log-Space Likelihoods**: Probabilities are calculated in log-space ($\sum \ln P(w_i \mid C)$) to prevent floating-point arithmetic underflow.
* **Laplace Smoothing ($\alpha = 0.5$)**: Adds a small pseudocount to handle unseen words and avoid zero-probability traps:
  $$P(w_i \mid C) = \frac{\text{count}(w_i, C) + \alpha}{\sum_{w} \text{count}(w, C) + \alpha \cdot |V|}$$

### 4. Multi-Class Topic Classification
In addition to binary spam detection, a softmax logistic regression classifier categorizes emails into **8 topics**:
1. `phishing` – Phishing & Credential Theft
2. `lottery` – Lottery & Sweepstakes Scams
3. `crypto_scam` – Bitcoin & Investment Scams
4. `marketing` – Commercial Promotions & Discounts
5. `work` – Workplace & Engineering Collaboration
6. `personal` – Personal & Social Messages
7. `notification` – Order Confirmations & Travel Alerts
8. `newsletter` – Newsletters & Educational Digests

### 5. Explainability (XAI) & Domain Heuristics
* **Token Attribution**: Explains *why* an email was classified as spam or ham by ranking tokens with the highest positive log-likelihood ratio (pushing towards spam) or negative ratio (pushing towards ham).
* **Domain Heuristics**: Evaluates uppercase character ratio (shouting), exclamation mark frequency, currency symbol counts, direct IP URLs, and mixed-script homoglyphs (e.g. Cyrillic letters spoofing Latin domain names).
* **Benign-Padding Resistance**: Inspects long messages in bounded chunks to prevent attackers from hiding short phishing payloads inside large blocks of benign text.

---

## ⚡ Core Features

- **Interactive Web Dashboard**: Built with Thymeleaf, HTML5, CSS3, and JavaScript featuring instant preset emails, a visual probability gauge, and structural signal breakdowns.
- **RESTful API**: Clean JSON endpoints supporting single-email and batch evaluations (1–100 emails).
- **Command-Line Interface (CLI)**: Terminal tool supporting single predictions, batch sample suites, holdout metrics, and an interactive prompt REPL.
- **Model Evaluation**: Deterministic 80/20 train/test holdout split evaluated on 2,600 emails with accuracy, precision, recall, F1, and confusion matrix.
- **Secure by Default**: In-memory rate limiting, maximum request body size caps, secure HTTP headers, and strict user-data privacy (no email bodies logged).

---

## 🛠️ Technology Stack

| Component | Technology | Description |
|---|---|---|
| **Language** | Java 21 | Modern LTS Java (records, text blocks, pattern matching) |
| **Framework** | Spring Boot 3.5.7 | Web, Thymeleaf, Validation, Testing |
| **Template Engine** | Thymeleaf | Server-rendered responsive HTML dashboard |
| **Build Tool** | Apache Maven 3.9+ | Dependency management and packaging |
| **Testing** | JUnit 5 & MockMvc | Comprehensive unit and integration test suite |
| **Containerization**| Docker | Multi-stage build on minimal Alpine JRE |
| **Architecture** | Controller-Service-DTO | Clean separation of concerns |

---

## 📁 Directory Structure

```text
├── Dockerfile                  # Multi-stage Docker build file
├── .dockerignore               # Docker build exclusions
├── pom.xml                     # Maven project descriptor
├── run.sh                      # Shell launcher for Web App
├── cli.sh                      # Shell launcher for CLI
├── src
│   ├── main
│   │   ├── java/com/classifier/spam
│   │   │   ├── SpamEmailClassifierApplication.java   # Spring Boot entry point
│   │   │   ├── cli/
│   │   │   │   └── SpamClassifierCli.java            # Terminal CLI tool
│   │   │   ├── config/
│   │   │   │   ├── ApiRateLimitFilter.java           # In-memory sliding window rate limiter
│   │   │   │   ├── RequestBodyLimitFilter.java       # 1MB payload buffer filter
│   │   │   │   └── SecurityHeadersFilter.java        # CSP, X-Frame-Options, No-Sniff
│   │   │   ├── dto/
│   │   │   │   ├── ClassificationRequest.java        # Single email request DTO
│   │   │   │   ├── BatchClassificationRequest.java   # Batch emails request DTO
│   │   │   │   ├── ClassificationResponse.java       # Detailed verdict response DTO
│   │   │   │   ├── BatchClassificationResponse.java  # Batch response envelope
│   │   │   │   └── ErrorResponse.java                # Global error envelope DTO
│   │   │   ├── service/
│   │   │   │   ├── emailclassification/
│   │   │   │   │   ├── EmailClassificationService.java     # Orchestration service
│   │   │   │   │   ├── NaiveBayesClassifierService.java    # Multinomial NB model
│   │   │   │   │   ├── TfidfVectorizer.java                # TF-IDF feature extractor
│   │   │   │   │   ├── TopicClassificationService.java     # 8-class softmax model
│   │   │   │   │   ├── ModelTrainingService.java           # 80/20 train/test evaluator
│   │   │   │   │   ├── CsvEmailDatasetLoader.java          # Multiline CSV parser
│   │   │   │   │   └── EmailTrainingExample.java           # Training record model
│   │   │   │   ├── emailrules/
│   │   │   │   │   └── HeuristicRuleEngineService.java     # Domain heuristics engine
│   │   │   │   └── textprocessing/
│   │   │   │       ├── PorterStemmer.java                  # English Porter stemmer
│   │   │   │       └── TextPreprocessingService.java       # NLP cleaning & tokens
│   │   │   └── web/
│   │   │       ├── ClassificationController.java     # Thymeleaf dashboard controller
│   │   │       ├── ClassificationApiController.java  # REST API controller
│   │   │       └── ApiExceptionHandler.java          # Global @RestControllerAdvice
│   │   └── resources
│   │       ├── application.properties                # Spring configuration
│   │       ├── dataset/
│   │       │   └── emails.csv                        # 2,600 emails benchmark dataset
│   │       ├── static/
│   │       │   ├── styles.css                        # Modern CSS styling
│   │       │   └── app.js                            # Dashboard UI script
│   │       └── templates/
│   │           └── index.html                        # Thymeleaf dashboard template
│   └── test
│       └── java/com/classifier/spam
│           ├── config/SecurityFiltersTest.java                  # Filter & security tests
│           ├── service/EmailClassificationServiceTest.java      # Service classification tests
│           ├── service/HeuristicRuleEngineServiceTest.java      # Rule engine tests
│           ├── service/TextPreprocessingServiceTest.java        # NLP & stemmer tests
│           └── web/ClassificationApiIntegrationTest.java        # MockMvc REST API tests
```

---

## 🚀 Getting Started

### Prerequisites
- **JDK 21** or higher
- **Maven 3.6.3** or higher
- **Docker** (optional)

### Run Locally

1. Start the application using Maven or the convenience script:
   ```zsh
   ./run.sh
   # or
   mvn spring-boot:run
   ```
2. Open your browser and navigate to:
   ```
   http://localhost:8080
   ```

To package and run as a standalone JAR:
```zsh
mvn clean package -DskipTests
java -jar target/spam-email-classifier-1.0.0.jar
```

### Run with Docker

1. Build the container image:
   ```zsh
   docker build -t spam-email-classifier .
   ```
2. Run the container:
   ```zsh
   docker run -d -p 8080:8080 --name spam-checker spam-email-classifier
   ```
3. Access the dashboard at `http://localhost:8080`.

### Run Unit & Integration Tests

Execute all 23 unit and integration tests:
```zsh
mvn test
```

---

## 💻 Command-Line Interface (CLI)

The project includes an interactive CLI launcher `./cli.sh`:

```zsh
# 1. Classify a single email (Formatted Card View)
./cli.sh predict --text "URGENT: Your PayPal account is suspended. Verify credentials now at http://paypal-fake.com"

# 2. Output as raw JSON (useful for piping into jq or scripts)
./cli.sh predict --text "Flight reservation confirmed for tomorrow" --json

# 3. Run built-in benchmark samples
./cli.sh test-samples

# 4. View held-out test evaluation metrics
./cli.sh metrics

# 5. Start interactive REPL prompt
./cli.sh interactive
```

---

## 📡 REST API Reference

### 1. Classify Single Email
- **Endpoint**: `POST /api/classify`
- **Request Body**:
  ```json
  {
    "text": "URGENT: Your PayPal account has been restricted. Click http://verify-paypal.com to unlock."
  }
  ```
- **Example cURL**:
  ```zsh
  curl -sS -X POST http://localhost:8080/api/classify \
    -H "Content-Type: application/json" \
    -d '{"text":"URGENT: Your PayPal account has been restricted. Click http://verify-paypal.com to unlock."}'
  ```
- **Response** (`200 OK`):
  ```json
  {
    "label": "spam",
    "is_spam": true,
    "confidence": 0.9997,
    "confidence_percent": 99.97,
    "spam_probability": 0.9997,
    "ham_probability": 0.0003,
    "topic": "phishing",
    "topic_display": "Phishing & Credential Alert",
    "topic_confidence": 28.22,
    "explanation": {
      "top_spam_words": [
        { "word": "verifi", "impact": 0.94, "tfidf": 0.35 },
        { "word": "paypal account", "impact": 0.92, "tfidf": 0.42 }
      ],
      "top_ham_words": [],
      "all_analyzed_tokens": 12
    },
    "heuristics": {
      "cap_ratio": 0.12,
      "currency_count": 0,
      "url_count": 1,
      "exclamation_ratio": 0.012
    }
  }
  ```

### 2. Batch Classification
- **Endpoint**: `POST /api/batch`
- **Request Body** (1–100 emails):
  ```json
  {
    "emails": [
      "Congratulations! You won $2.5M in the sweepstakes.",
      "Hi team, please find the sprint report attached."
    ]
  }
  ```
- **Example cURL**:
  ```zsh
  curl -sS -X POST http://localhost:8080/api/batch \
    -H "Content-Type: application/json" \
    -d '{"emails":["Congratulations! You won $2.5M.", "Hi team, sprint retro is at 2 PM."]}'
  ```

### 3. Model Benchmark Metrics
- **Endpoint**: `GET /api/metrics`
- **Response**: Returns accuracy, precision, recall, F1, ROC-AUC, and confusion matrix on the held-out test split.

### 4. System Health Check
- **Endpoint**: `GET /api/health`
- **Response**: `{"status": "ok", "model": "ready"}`

---

## 🔒 Security & Production Safeguards

1. **Input Validation**: Uses `jakarta.validation` (`@NotBlank`, `@Size(max = 50_000)`) on request payloads.
2. **In-Memory Rate Limiting**: `ApiRateLimitFilter` throttles single-email requests to 30/min and batch requests to 10/min per IP address. Responds with `429 Too Many Requests` and a `Retry-After` header.
3. **Payload Buffering & Size Caps**: `RequestBodyLimitFilter` strictly caps request bodies at 1 MB to prevent memory exhaustion / DoS attacks.
4. **No Stack Trace Exposure**: `ApiExceptionHandler` handles exceptions globally and returns consistent, safe JSON responses (`ErrorResponse`). Stack traces are logged server-side and never returned to the client.
5. **Data Privacy**: The server logs only request metadata (character count, result label, confidence) — never the user's private email body.
6. **Hardened HTTP Headers**: Automatically sets `Content-Security-Policy`, `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, and `Cache-Control: no-store`.

---

## 🎤 How to Explain This Project in an Interview

When explaining this project to a technical interviewer, use this 4-step framework:

1. **The Problem & What You Built**:
   > *"I built an end-to-end Spam Email Classifier and Safety Checker using Java 21 and Spring Boot. Rather than relying on external black-box APIs or Python sidecars, I implemented the NLP preprocessing, TF-IDF feature extraction, and Multinomial Naive Bayes classification directly in native Java."*

2. **The Architecture**:
   > *"I structured the application using a clean Controller-Service-DTO pattern. Incoming requests pass through security filters for in-memory rate limiting and request body size caps. Controllers validate inputs using Jakarta Validation and map requests to DTOs. The service layer coordinates natural language preprocessing, feature extraction, and classification, returning an explainable result with confidence scores, topic tags, and top contributing tokens."*

3. **The Machine Learning Implementation**:
   > *"For text vectorization, I implemented TF-IDF with sublinear term frequency scaling and unigram plus bigram extraction, which allows the model to capture context like 'verify account' or 'wire transfer'. Classification uses Multinomial Naive Bayes in log-space with Laplace smoothing to avoid zero-frequency problems. In addition, I implemented a multi-class softmax classifier for 8 email topics and a domain heuristic engine to detect structural threats like mixed-script homoglyph domains and high-urgency keywords."*

4. **Production Readiness & Testing**:
   > *"To make it robust and easy to deploy, I wrote 23 unit and integration tests using JUnit 5 and Spring's MockMvc, covering validation edges, rate limiting, and classification accuracy. I packaged the application using a multi-stage Alpine Dockerfile and added global exception handling to ensure internal stack traces are never leaked."*

---

## 📄 License
This project is open-source and available under the [MIT License](LICENSE).
