package com.classifier.spam.service;

import com.classifier.spam.service.textprocessing.PorterStemmer;
import com.classifier.spam.service.textprocessing.TextPreprocessingService;
import com.classifier.spam.service.textprocessing.TextPreprocessingService.ExtractedFeatures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TextPreprocessingServiceTest {

    private TextPreprocessingService preprocessingService;

    @BeforeEach
    void setUp() {
        preprocessingService = new TextPreprocessingService(new PorterStemmer());
    }

    @Test
    void cleanText_normalizesEntitiesAndStripsHtml() {
        String input = "<p>Claim $5,000 prize now at https://lottery.xyz or call 800-555-1234!</p>";
        String cleaned = preprocessingService.cleanText(input);

        assertTrue(cleaned.contains("token_money"));
        assertTrue(cleaned.contains("token_url"));
        assertTrue(cleaned.contains("token_phone"));
        assertFalse(cleaned.contains("<p>"));
    }

    @Test
    void tokenizeAndStem_retainsImportantWordsAndStems() {
        String input = "Urgent winning notification: winners were selected!";
        List<String> tokens = preprocessingService.tokenizeAndStem(input);

        assertFalse(tokens.isEmpty());
        assertTrue(tokens.contains("urgent"));
        assertTrue(tokens.contains("notif"));
    }

    @Test
    void extractFeatures_measuresPunctuationAndFormatting() {
        String input = "URGENT ACTION REQUIRED! Win $1,000,000 cash now! Visit http://scam.xyz";
        ExtractedFeatures features = preprocessingService.extractFeatures(input);

        assertTrue(features.getCapitalLetterRatio() > 0.3);
        assertTrue(features.getCurrencyCount() >= 1);
        assertTrue(features.getUrlCount() >= 1);
        assertTrue(features.getExclamationRatio() > 0);
    }
}
