package com.classifier.spam.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class ApiRateLimitFilter extends OncePerRequestFilter {
    private static final long WINDOW_MILLIS = 60_000;
    private static final int MAX_CLASSIFY_REQUESTS_PER_WINDOW = 30;
    private static final int MAX_BATCH_REQUESTS_PER_WINDOW = 10;
    private static final int MAX_TRACKED_CLIENTS = 10_000;

    private final Map<String, RequestWindow> clients = new LinkedHashMap<>(128, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, RequestWindow> eldest) {
            return size() > MAX_TRACKED_CLIENTS;
        }
    };

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!isClassificationRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        long now = System.currentTimeMillis();
        long retryAfterSeconds = 0;
        String path = request.getServletPath();
        int requestLimit = "/api/batch".equals(path)
                ? MAX_BATCH_REQUESTS_PER_WINDOW : MAX_CLASSIFY_REQUESTS_PER_WINDOW;
        synchronized (clients) {
            String clientKey = request.getRemoteAddr() + '|' + path;
            RequestWindow window = clients.computeIfAbsent(clientKey, ignored -> new RequestWindow(now));
            if (now - window.startedAtMillis >= WINDOW_MILLIS) {
                window.startedAtMillis = now;
                window.requestCount = 0;
            }

            if (window.requestCount >= requestLimit) {
                retryAfterSeconds = Math.max(1, (window.startedAtMillis + WINDOW_MILLIS - now + 999) / 1000);
            } else {
                window.requestCount++;
            }
        }

        if (retryAfterSeconds == 0) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(429);
        response.setHeader("Retry-After", Long.toString(retryAfterSeconds));
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\":\"Too many classification requests. Try again shortly.\"}");
    }

    private boolean isClassificationRequest(HttpServletRequest request) {
        if (!"POST".equals(request.getMethod())) return false;
        String path = request.getServletPath();
        return "/api/classify".equals(path) || "/api/batch".equals(path);
    }

    private static final class RequestWindow {
        private long startedAtMillis;
        private int requestCount;

        private RequestWindow(long startedAtMillis) {
            this.startedAtMillis = startedAtMillis;
        }
    }
}
