package com.queueless.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.queueless.entity.Shop;
import com.queueless.entity.Token;
import com.queueless.repository.TokenRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * AI Prediction Service powered by Google Gemini via REST API.
 * Uses WebClient to call the Gemini generateContent endpoint directly.
 * No SDK required — just a Gemini API key from Google AI Studio.
 */
@Service
@Slf4j
public class AIPredictionService {

    private static final String GEMINI_API_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final TokenRepository tokenRepository;

    @Value("${gemini.api-key:}")
    private String geminiApiKey;

    @Value("${gemini.model:gemini-1.5-flash}")
    private String geminiModel;

    public AIPredictionService(WebClient.Builder webClientBuilder,
                               ObjectMapper objectMapper,
                               TokenRepository tokenRepository) {
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
        this.tokenRepository = tokenRepository;
    }

    /**
     * Calls Gemini REST API with a plain text prompt and returns the response text.
     */
    private String callGemini(String prompt) {
        if (geminiApiKey == null || geminiApiKey.isBlank()) {
            throw new IllegalStateException("GEMINI_API_KEY is not configured");
        }

        String url = String.format(GEMINI_API_URL, geminiModel, geminiApiKey);

        // Gemini request body: {"contents":[{"parts":[{"text":"..."}]}]}
        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(
                                Map.of("text", prompt)
                        ))
                )
        );

        String responseJson = webClient.post()
                .uri(url)
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(10));

        // Parse: candidates[0].content.parts[0].text
        JsonNode root = parseJson(responseJson);
        return root.path("candidates").get(0)
                .path("content").path("parts").get(0)
                .path("text").asText().trim();
    }

    private JsonNode parseJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Gemini response: " + json, e);
        }
    }

    /**
     * Predicts dynamic wait time using recent historical completions.
     * Falls back to static formula if Gemini is not configured or fails.
     */
    public int predictWaitTime(Shop shop, Token token, int currentQueueSize, List<Token> recentHistory) {
        int staticEstimate = currentQueueSize * shop.getAvgServiceMins();

        if (recentHistory.isEmpty() || geminiApiKey == null || geminiApiKey.isBlank()) {
            return staticEstimate;
        }

        try {
            String historySummary = recentHistory.stream()
                    .map(t -> {
                        long mins = t.getServedAt() != null && t.getCalledAt() != null
                                ? Duration.between(t.getCalledAt(), t.getServedAt()).toMinutes()
                                : shop.getAvgServiceMins();
                        return String.valueOf(mins);
                    })
                    .collect(Collectors.joining(", "));

            String prompt = String.format(
                    "You are an AI Wait Time Predictor for a queue management system. " +
                    "Analyze recent service durations (in minutes) and output ONLY a single integer " +
                    "representing the estimated total wait time in minutes. " +
                    "Static average is %d mins. Current queue size ahead: %d. " +
                    "Recent service durations: [%s]. Output ONLY the integer, nothing else.",
                    shop.getAvgServiceMins(), currentQueueSize, historySummary);

            String response = callGemini(prompt);
            int aiEstimate = Integer.parseInt(response.replaceAll("[^0-9]", ""));

            // Weighted blend to prevent wild swings
            return (staticEstimate + aiEstimate) / 2;

        } catch (Exception e) {
            log.warn("Gemini Wait Time Prediction failed, using static fallback: {}", e.getMessage());
            return staticEstimate;
        }
    }

    /**
     * Predicts the probability (0.0–1.0) that a token will result in a no-show.
     */
    public double predictNoShowProbability(int snoozeCount, int rejoinCount, int currentWaitTimeEstimate) {
        if (geminiApiKey == null || geminiApiKey.isBlank()) return 0.0;

        try {
            String prompt = String.format(
                    "You are an AI No-Show Risk Predictor for a queue management system. " +
                    "Predict the probability (0.0 to 1.0) that a customer will abandon the queue. " +
                    "Higher snooze counts, longer wait times, and more rejoins indicate higher risk. " +
                    "Customer details: Snooze Count=%d, Rejoin Count=%d, Estimated Wait=%d minutes. " +
                    "Output ONLY a decimal number between 0.0 and 1.0 (e.g. 0.72), nothing else.",
                    snoozeCount, rejoinCount, currentWaitTimeEstimate);

            String response = callGemini(prompt);
            double probability = Double.parseDouble(response.replaceAll("[^0-9.]", ""));
            return Math.min(1.0, Math.max(0.0, probability)); // clamp to [0,1]

        } catch (Exception e) {
            log.warn("Gemini No-Show Prediction failed: {}", e.getMessage());
            return 0.0;
        }
    }

    /**
     * Computes the no-show probability asynchronously and saves it to the token.
     */
    public void computeAndSaveNoShowProbabilityAsync(java.util.UUID tokenId, int snoozeCount, int rejoinCount, int currentWaitTimeEstimate) {
        CompletableFuture.runAsync(() -> {
            try {
                double prob = predictNoShowProbability(snoozeCount, rejoinCount, currentWaitTimeEstimate);
                Token token = tokenRepository.findById(tokenId).orElse(null);
                if (token != null) {
                    token.setNoShowProbability(prob);
                    tokenRepository.save(token);
                }
            } catch (Exception e) {
                log.warn("Failed to save no-show probability for token {}: {}", tokenId, e.getMessage());
            }
        });
    }
}
