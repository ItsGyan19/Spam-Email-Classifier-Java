# Email Safety Checker

Email Safety Checker is a Java web application that checks whether an email looks like spam or a regular message. It also predicts the email topic and shows words and details that influenced the result. You can use the browser dashboard, send requests to the REST API, or run the command-line tool.

## Technologies and versions

- Java 21
- Spring Boot 3.5.7
- Maven 3.6.3 or newer
- Spring MVC and Thymeleaf for the web application and dashboard
- HTML, CSS, and JavaScript for the browser interface

## How it works

1. When the application starts, it loads 2,600 sample emails from `src/main/resources/dataset/emails.csv`.
2. It makes a repeatable split: 2,080 emails are used to train the classifiers and 520 are held back to measure their performance.
3. When you submit an email, the app cleans and prepares its text, then checks its words and message details.
4. A spam check predicts spam or regular email. A separate topic check predicts one of eight topics, such as phishing, marketing, work, or newsletters.
5. The app returns the predictions, probabilities, and clues. The dashboard displays the results.

The classifiers are implemented in Java and trained each time the application starts. Their measured results describe the included sample dataset; they do not guarantee performance on real-world email.

## Features

- Check one email or a batch of up to 100 emails
- Try built-in sample messages from the dashboard
- View spam or regular-email probabilities and a predicted topic
- See words and message details that influenced the result
- Review measured performance on the held-back sample emails
- Use the same checks from a command-line interface

## Project folders in plain language

- `web`: browser pages and REST API routes.
- `config`: request-size limits, rate limits, and browser security headers.
- `service/emailclassification`: training the classifiers and producing spam/topic predictions. “ML” means machine learning: the program learns patterns from example emails.
- `service/textprocessing`: cleaning email text and splitting it into useful words. “NLP” means natural language processing, which is the general name for working with human language in software.
- `service/emailrules`: extra checks for suspicious email patterns. A “heuristic” is a practical clue or rule; it is not proof that a message is spam.
- `cli`: a command-line way to try the classifier.

## Java implementation

- Java 21 and Spring Boot (the version is managed by the parent in `pom.xml`)
- Spring MVC and Thymeleaf dashboard
- Native Java text preprocessor with URL/email/currency/phone/number normalization, stopword filtering, and Porter stemming
- TF-IDF with sublinear term frequency, unigrams and bigrams, `min_df=2`, up to 3,000 spam features and 2,500 topic features
- Multinomial Naive Bayes with `alpha=0.5`; eight-topic logistic-style softmax classifier
- Spam/ham probabilities, topic confidence, structural features, and top positive/negative TF-IDF feature contributions
- Single-email and batch JSON endpoints, plus a browser dashboard and measured holdout metrics

The application trains a Multinomial Naive Bayes spam classifier and reports stratified 80/20 holdout results. Its topic classifier uses TF-IDF features with a multiclass softmax model. These measured results describe the included dataset and may differ on real-world email.

## Dataset and evaluation

The dataset is `src/main/resources/dataset/emails.csv`. At startup, the app reads its multiline CSV records, makes a deterministic stratified 80/20 split (seed 42), trains both models on 2,080 rows, and evaluates them on 520 held-out rows. `GET /api/metrics` returns accuracy, precision, recall, F1, ROC-AUC, confusion matrix, and topic accuracy.

The included dataset is curated and does not establish real-world spam-filter performance. Evaluate against representative unseen email before using this as a filter.

## Requirements and startup

- JDK 21 (or compatible OpenJDK 21+)
- Maven 3.6.3 or newer
- Docker (optional, for containerized run)

### Start Web Server

```zsh
./run.sh
# or
mvn spring-boot:run
```

The application uses port 8080 locally by default. Open <http://localhost:8080> in your browser.

To package and run as a standalone JAR:

```zsh
mvn clean package
java -jar target/spam-email-classifier-1.0.0.jar
```

### Run Tests

```zsh
mvn test
```

### Run with Docker

```zsh
docker build -t spam-email-classifier .
docker run -p 8080:8080 spam-email-classifier
```

## Command-line interface (CLI)

Use the `./cli.sh` launcher (or run `com.classifier.spam.cli.SpamClassifierCli` directly from your IDE):

```zsh
# Classify a single email (Formatted Card View)
./cli.sh predict --text "URGENT: Your PayPal account has been suspended! Verify here: http://fake-paypal.com"

# Classify as JSON output
./cli.sh predict --text "Flight reservation confirmed for tomorrow" --json

# Run benchmark test suite across 5 preset categories
./cli.sh test-samples

# View held-out test evaluation metrics (Accuracy, F1, Confusion Matrix)
./cli.sh metrics

# Start interactive REPL
./cli.sh interactive
```

## Browser dashboard

The dashboard at `/` provides sample presets, a spam/ham probability gauge, topic badge, structural features, top spam/ham words, and a model evaluation table.

## REST API

### `POST /api/classify`

```zsh
curl -sS -X POST http://localhost:8080/api/classify \
  -H 'Content-Type: application/json' \
  -d '{"text":"URGENT: Your account has been restricted. Click http://verify-paypal.com to unlock."}'
```

The response includes `label`, `is_spam`, spam/ham probabilities, `topic` and topic confidence, top spam/ham feature contributions, and the structural heuristic values. Empty text returns 400. Email text is limited to 50,000 characters and request bodies to 2 MB.

### `POST /api/batch`

```zsh
curl -sS -X POST http://localhost:8080/api/batch \
  -H 'Content-Type: application/json' \
  -d '{"emails":["Congratulations! You won a prize.","Hi team, the meeting is at 2 PM."]}'
```

Batch requests accept 1–100 emails and return `{ "count": ..., "results": [...] }`.

### `GET /api/metrics`, `GET /api/stats`, `GET /api/health`

`/api/metrics` returns measured Java holdout metrics, `/api/stats` returns training and vocabulary counts plus the evaluation, and `/api/health` reports whether the app started with its models loaded.

## Security and scope

- Request bodies are limited to 1 MB; each email is limited to 50,000 characters and each batch to 100 messages. Tomcat worker threads and connections are capped to bound concurrent memory and CPU use.
- Classification requests are throttled per client address: 30 single-email checks and 10 batch requests per minute. Excess requests receive HTTP 429 and a `Retry-After` header.
- Tomcat concurrency is capped, and long messages receive a bounded check of several segments so a short spam message is less easily hidden by benign text. Mixed Latin/Cyrillic or Latin/Greek domains are flagged as suspicious.
- Null bytes are stripped before preprocessing. Invalid JSON and validation failures return concise errors; stack traces and internal exception details are not returned.
- The Java application does not load serialized model files. It trains from the bundled CSV dataset at startup, so Python `joblib`/`pickle` model-loading risks and Flask's development debugger do not apply to this implementation.
- The dashboard uses local static assets, escaped/constructed text output, a restrictive content security policy, and browser security headers. API responses are marked `no-store`.
- The endpoints remain unauthenticated for local demonstration. Rate limits are in-memory and per application instance; the text-analysis checks are heuristics, not a guarantee against denial of service, phishing, or adversarial messages. Add authentication, HTTPS, and gateway-level controls before exposing the service publicly.
- The app stores no email submissions and requires no secrets or database.
