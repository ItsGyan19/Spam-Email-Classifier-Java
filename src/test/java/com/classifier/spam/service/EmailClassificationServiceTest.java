package com.classifier.spam.service;

import com.classifier.spam.service.emailclassification.EmailClassificationService;
import com.classifier.spam.service.emailclassification.EmailClassificationService.ClassificationResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class EmailClassificationServiceTest {

    @Autowired
    private EmailClassificationService service;

    @Test
    void classifyEmail_correctlyIdentifiesPhishingSpam() {
        String phishingEmail = "URGENT: Your PayPal account has been temporarily restricted. " +
                "Click here to verify your identity immediately: http://paypal-verify-fake.com";

        ClassificationResponse response = service.classifyEmail(phishingEmail);

        assertTrue(response.isSpam());
        assertEquals("spam", response.label());
        assertTrue(response.spamProbability() > 0.85);
        assertEquals("phishing", response.topic());
        assertNotNull(response.explanation());
        assertFalse(response.explanation().topSpamWords().isEmpty());
    }

    @Test
    void classifyEmail_correctlyIdentifiesLegitimateWorkEmail() {
        String workEmail = "Hi team, please find attached the weekly sprint report before our engineering sync tomorrow.";

        ClassificationResponse response = service.classifyEmail(workEmail);

        assertFalse(response.isSpam());
        assertEquals("ham", response.label());
        assertTrue(response.hamProbability() > 0.85);
        assertEquals("work", response.topic());
        assertNotNull(response.explanation());
        assertFalse(response.explanation().topHamWords().isEmpty());
    }

    @Test
    void classifyEmail_handlesEmptyGracefully() {
        ClassificationResponse response = service.classifyEmail("");

        assertFalse(response.isSpam());
        assertEquals("ham", response.label());
        assertEquals("unknown", response.topic());
    }
}
