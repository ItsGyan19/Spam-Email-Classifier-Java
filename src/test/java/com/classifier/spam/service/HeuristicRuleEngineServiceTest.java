package com.classifier.spam.service;

import com.classifier.spam.service.emailrules.HeuristicRuleEngineService;
import com.classifier.spam.service.textprocessing.PorterStemmer;
import com.classifier.spam.service.textprocessing.TextPreprocessingService;
import com.classifier.spam.service.textprocessing.TextPreprocessingService.ExtractedFeatures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HeuristicRuleEngineServiceTest {

    private HeuristicRuleEngineService ruleEngine;
    private TextPreprocessingService preprocessing;

    @BeforeEach
    void setUp() {
        ruleEngine = new HeuristicRuleEngineService();
        preprocessing = new TextPreprocessingService(new PorterStemmer());
    }

    @Test
    void analyze_detectsUrgencyAndFinancialScam() {
        String email = "URGENT ACTION REQUIRED: You have won the lottery! Claim your prize within 24 hours.";
        ExtractedFeatures features = preprocessing.extractFeatures(email);

        HeuristicRuleEngineService.HeuristicAnalysisResult result =
                ruleEngine.analyze("Important Notification", "security@bank.xyz", email, features);

        assertTrue(result.getTotalRiskScore() > 40.0);
        assertTrue(result.getTriggers().stream().anyMatch(t -> t.getRuleId().equals("RULE_URGENCY")));
        assertTrue(result.getTriggers().stream().anyMatch(t -> t.getRuleId().equals("RULE_FINANCIAL_SCAM")));
    }

    @Test
    void analyze_givesLowRiskForNormalWorkEmail() {
        String email = "Hi team, please find attached the agenda for our weekly sprint sync tomorrow at 10 AM.";
        ExtractedFeatures features = preprocessing.extractFeatures(email);

        HeuristicRuleEngineService.HeuristicAnalysisResult result =
                ruleEngine.analyze("Sprint sync", "lead@company.com", email, features);

        assertEquals(0.0, result.getTotalRiskScore(), 0.01);
        assertTrue(result.getTriggers().isEmpty());
    }
}
