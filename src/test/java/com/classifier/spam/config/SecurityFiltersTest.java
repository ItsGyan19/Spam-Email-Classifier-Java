package com.classifier.spam.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityFiltersTest {
    private static final String CLIENT_ADDRESS = "192.0.2.42";
    private static final FilterChain OK_CHAIN = (request, response) ->
            ((HttpServletResponse) response).setStatus(HttpServletResponse.SC_OK);

    @Test
    void allowsThirtySingleChecksThenReturnsTooManyRequests() throws Exception {
        ApiRateLimitFilter filter = new ApiRateLimitFilter();

        for (int i = 0; i < 30; i++) {
            MockHttpServletResponse response = sendClassificationRequest(filter, "/api/classify");
            assertEquals(HttpServletResponse.SC_OK, response.getStatus());
        }

        MockHttpServletResponse limitedResponse = sendClassificationRequest(filter, "/api/classify");
        assertEquals(429, limitedResponse.getStatus());
        assertTrue(Integer.parseInt(limitedResponse.getHeader("Retry-After")) > 0);
        assertTrue(limitedResponse.getContentAsString().contains("Too many classification requests"));
    }

    @Test
    void allowsTenBatchChecksThenReturnsTooManyRequests() throws Exception {
        ApiRateLimitFilter filter = new ApiRateLimitFilter();

        for (int i = 0; i < 10; i++) {
            MockHttpServletResponse response = sendClassificationRequest(filter, "/api/batch");
            assertEquals(HttpServletResponse.SC_OK, response.getStatus());
        }

        MockHttpServletResponse limitedResponse = sendClassificationRequest(filter, "/api/batch");
        assertEquals(429, limitedResponse.getStatus());
        assertTrue(Integer.parseInt(limitedResponse.getHeader("Retry-After")) > 0);
    }

    @Test
    void addsBrowserSecurityHeadersAndPreventsApiCaching() throws Exception {
        SecurityHeadersFilter filter = new SecurityHeadersFilter();
        MockHttpServletRequest request = requestFor("/api/classify");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, OK_CHAIN);

        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
        assertEquals("DENY", response.getHeader("X-Frame-Options"));
        assertEquals("camera=(), microphone=(), geolocation=()", response.getHeader("Permissions-Policy"));
        assertEquals("no-store", response.getHeader("Cache-Control"));
        assertTrue(response.getHeader("Content-Security-Policy").contains("object-src 'none'"));
        assertTrue(response.getHeader("Content-Security-Policy").contains("frame-ancestors 'none'"));
    }

    @Test
    void rejectsRequestBodiesLargerThanOneMegabyte() throws Exception {
        RequestBodyLimitFilter filter = new RequestBodyLimitFilter();
        MockHttpServletRequest request = requestFor("/api/classify");
        request.setContent(new byte[1024 * 1024 + 1]);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (incomingRequest, incomingResponse) ->
                ((HttpServletResponse) incomingResponse).setStatus(HttpServletResponse.SC_OK));

        assertEquals(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, response.getStatus());
        assertTrue(response.getContentAsString().contains("Maximum allowed request size is 1 Megabyte"));
    }

    private MockHttpServletResponse sendClassificationRequest(ApiRateLimitFilter filter, String path) throws Exception {
        MockHttpServletRequest request = requestFor(path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, OK_CHAIN);
        return response;
    }

    private MockHttpServletRequest requestFor(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setServletPath(path);
        request.setRemoteAddr(CLIENT_ADDRESS);
        return request;
    }
}
