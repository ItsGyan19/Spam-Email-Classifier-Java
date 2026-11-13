package com.classifier.spam.service.textprocessing;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TextPreprocessingService {
    private static final Set<String> STOPWORDS = Set.of(
            "i", "me", "my", "myself", "we", "our", "ours", "ourselves", "you", "your", "yours",
            "yourself", "yourselves", "he", "him", "his", "himself", "she", "her", "herself", "it",
            "its", "itself", "they", "them", "their", "theirs", "themselves", "what", "which", "who",
            "whom", "this", "that", "these", "those", "am", "is", "are", "was", "were", "be", "been",
            "being", "have", "has", "had", "having", "do", "does", "did", "doing", "a", "an", "the",
            "and", "but", "if", "or", "because", "as", "until", "while", "of", "at", "by", "for", "with",
            "about", "against", "between", "into", "through", "during", "before", "after", "above", "below",
            "to", "from", "up", "down", "in", "out", "on", "off", "over", "under", "again", "further",
            "then", "once", "here", "there", "when", "where", "why", "how", "all", "any", "both", "each",
            "few", "more", "most", "other", "some", "such", "nor", "too", "very", "s", "t", "can", "will",
            "just", "don", "should", "now", "d", "ll", "m", "o", "re", "ve", "y", "ain", "aren", "couldn",
            "didn", "doesn", "hadn", "hasn", "haven", "isn", "ma", "mightn", "mustn", "needn", "shan",
            "shouldn", "wasn", "weren", "won", "wouldn"
    );
    private static final Set<String> STOPWORDS_TO_KEEP = Set.of("free", "urgent", "win", "won", "no", "not", "only", "now");
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
    private static final Pattern EMAIL = Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b");
    private static final Pattern URL = Pattern.compile("https?://\\S+|www\\.\\S+|[a-zA-Z0-9.-]+\\.(?:com|org|net|biz|xyz|info|cc|shop|me|io)\\b\\S*", Pattern.CASE_INSENSITIVE);
    private static final Pattern CURRENCY = Pattern.compile("[$£€¥₹]\\s*\\d+(?:[.,]\\d+)?|\\b\\d+(?:[.,]\\d+)?\\s*(?:dollars?|usd|eur|gbp|btc|bitcoin)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PHONE = Pattern.compile("\\b(?:\\+?\\d{1,3}[-.\\s]?)?\\(?\\d{3}\\)?[-.\\s]?\\d{3}[-.\\s]?\\d{4}\\b");
    private static final Pattern NUMBER = Pattern.compile("\\b\\d+\\b");
    private static final Pattern REPEATED_EXCLAMATION = Pattern.compile("!{2,}");
    private static final Pattern SPACE = Pattern.compile("\\s+");
    private static final Pattern TOKEN = Pattern.compile("(?U)\\b\\w+\\b");
    private static final Pattern URL_SIGNAL = Pattern.compile("https?://|www\\.|\\.(?:com|org|net|biz|xyz|info)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DOMAIN = Pattern.compile(
            "(?i)(?<![\\p{L}\\p{N}@])(?:https?://|www\\.)?((?:[\\p{L}\\p{N}-]+\\.)+(?:com|org|net|biz|xyz|info|cc|shop|me|io))(?![\\p{L}\\p{N}])");
    private static final Pattern CURRENCY_SIGNAL = Pattern.compile("[$€£¥₹]");

    private final PorterStemmer stemmer;

    public TextPreprocessingService(PorterStemmer stemmer) {
        this.stemmer = stemmer;
    }

    public String cleanText(String rawText) {
        if (rawText == null || rawText.isEmpty()) return "";
        String cleaned = HTML_TAG.matcher(rawText).replaceAll(" ");
        cleaned = EMAIL.matcher(cleaned).replaceAll(" token_email ");
        cleaned = URL.matcher(cleaned).replaceAll(" token_url ");
        cleaned = CURRENCY.matcher(cleaned).replaceAll(" token_money ");
        cleaned = PHONE.matcher(cleaned).replaceAll(" token_phone ");
        cleaned = NUMBER.matcher(cleaned).replaceAll(" token_num ");
        cleaned = REPEATED_EXCLAMATION.matcher(cleaned).replaceAll(" token_exclamation ");
        cleaned = cleaned.toLowerCase(Locale.ROOT);
        cleaned = cleaned.replaceAll("[\\p{Punct}&&[^_]]", "");
        return SPACE.matcher(cleaned).replaceAll(" ").strip();
    }

    public List<String> tokenizeAndStem(String rawText) {
        Matcher matcher = TOKEN.matcher(cleanText(rawText));
        List<String> tokens = new ArrayList<>();
        while (matcher.find()) {
            String token = matcher.group();
            if (token.length() <= 1 || (STOPWORDS.contains(token) && !STOPWORDS_TO_KEEP.contains(token))) continue;
            tokens.add(stemmer.stem(token));
        }
        return tokens;
    }

    public ExtractedFeatures extractFeatures(String rawText) {
        String text = rawText == null ? "" : rawText;
        int capitalCharacters = 0;
        int digits = 0;
        int exclamationCount = 0;
        int questionCount = 0;
        int totalCharacters = text.codePointCount(0, text.length());
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            if (Character.isUpperCase(codePoint)) capitalCharacters++;
            if (Character.isDigit(codePoint)) digits++;
            if (codePoint == '!') exclamationCount++;
            if (codePoint == '?') questionCount++;
            offset += Character.charCount(codePoint);
        }
        String trimmed = text.strip();
        int wordCount = trimmed.isEmpty() ? 0 : trimmed.split("\\s+").length;
        int allCapsWords = 0;
        for (String word : trimmed.isEmpty() ? new String[0] : trimmed.split("\\s+")) {
            boolean containsLetter = word.codePoints().anyMatch(Character::isLetter);
            if (word.length() > 1 && containsLetter && word.equals(word.toUpperCase(Locale.ROOT))) allCapsWords++;
        }
        int currencyCount = countMatches(CURRENCY_SIGNAL, text);
        int urlCount = countMatches(URL_SIGNAL, text);
        int suspiciousUnicodeUrlCount = countSuspiciousUnicodeUrls(text);
        double capRatio = totalCharacters == 0 ? 0.0 : (double) capitalCharacters / totalCharacters;
        return new ExtractedFeatures(tokenizeAndStem(text), urlCount, suspiciousUnicodeUrlCount,
                currencyCount, exclamationCount,
                questionCount, capRatio, totalCharacters == 0 ? 0.0 : (double) exclamationCount / totalCharacters,
                totalCharacters == 0 ? 0.0 : (double) digits / totalCharacters,
                Math.log1p(wordCount), wordCount == 0 ? 0.0 : (double) allCapsWords / wordCount);
    }

    private int countSuspiciousUnicodeUrls(String text) {
        int suspiciousCount = 0;
        Matcher matcher = DOMAIN.matcher(text);
        while (matcher.find()) {
            boolean hasLatin = false;
            boolean hasLookalikeScript = false;
            String domain = matcher.group(1);
            for (int offset = 0; offset < domain.length();) {
                int codePoint = domain.codePointAt(offset);
                Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
                hasLatin |= script == Character.UnicodeScript.LATIN;
                hasLookalikeScript |= script == Character.UnicodeScript.CYRILLIC ||
                        script == Character.UnicodeScript.GREEK;
                offset += Character.charCount(codePoint);
            }
            if (hasLatin && hasLookalikeScript) suspiciousCount++;
        }
        return suspiciousCount;
    }

    private int countMatches(Pattern pattern, String text) {
        int count = 0;
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) count++;
        return count;
    }

    public static class ExtractedFeatures {
        private final List<String> stemmedTokens;
        private final int urlCount;
        private final int suspiciousUnicodeUrlCount;
        private final int currencyCount;
        private final int exclamationCount;
        private final int questionCount;
        private final double capitalLetterRatio;
        private final double exclamationRatio;
        private final double digitRatio;
        private final double normalizedWordCount;
        private final double allCapsWordRatio;

        public ExtractedFeatures(List<String> stemmedTokens, int urlCount, int suspiciousUnicodeUrlCount,
                                 int currencyCount,
                                 int exclamationCount, int questionCount, double capitalLetterRatio,
                                 double exclamationRatio, double digitRatio, double normalizedWordCount,
                                 double allCapsWordRatio) {
            this.stemmedTokens = List.copyOf(stemmedTokens);
            this.urlCount = urlCount;
            this.suspiciousUnicodeUrlCount = suspiciousUnicodeUrlCount;
            this.currencyCount = currencyCount;
            this.exclamationCount = exclamationCount;
            this.questionCount = questionCount;
            this.capitalLetterRatio = capitalLetterRatio;
            this.exclamationRatio = exclamationRatio;
            this.digitRatio = digitRatio;
            this.normalizedWordCount = normalizedWordCount;
            this.allCapsWordRatio = allCapsWordRatio;
        }

        public List<String> getStemmedTokens() { return stemmedTokens; }
        public int getUrlCount() { return urlCount; }
        public int getSuspiciousUnicodeUrlCount() { return suspiciousUnicodeUrlCount; }
        public int getCurrencyCount() { return currencyCount; }
        public int getExclamationCount() { return exclamationCount; }
        public int getQuestionCount() { return questionCount; }
        public double getCapitalLetterRatio() { return capitalLetterRatio; }
        public double getExclamationRatio() { return exclamationRatio; }
        public double getDigitRatio() { return digitRatio; }
        public double getNormalizedWordCount() { return normalizedWordCount; }
        public double getAllCapsWordRatio() { return allCapsWordRatio; }
    }
}
