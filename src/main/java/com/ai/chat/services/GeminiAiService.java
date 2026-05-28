package com.ai.chat.services;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import com.ai.chat.models.ChatMessage;

@Service
public class GeminiAiService {

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.model}")
    private String model;

    private final RestTemplate restTemplate = new RestTemplate();

    public String askGemini(List<ChatMessage> history, String userMessage) {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                + model + ":generateContent?key=" + apiKey;

        List<Map<String, Object>> contents = new ArrayList<>();

        // Sanitize: Gemini requires strictly alternating user/model turns
        List<ChatMessage> sanitized = new ArrayList<>();
        String expectedRole = "user";
        for (ChatMessage msg : history) {
            String msgRole = msg.getRole().equals("assistant") ? "model" : "user";
            if (msgRole.equals(expectedRole)) {
                sanitized.add(msg);
                expectedRole = expectedRole.equals("user") ? "model" : "user";
            }
        }
        // Drop trailing user message (incomplete pair)
        if (!sanitized.isEmpty() && sanitized.get(sanitized.size() - 1).getRole().equals("user")) {
            sanitized.remove(sanitized.size() - 1);
        }

        for (ChatMessage msg : sanitized) {
            String role = msg.getRole().equals("assistant") ? "model" : "user";
            contents.add(Map.of(
                    "role", role,
                    "parts", List.of(Map.of("text", msg.getContent()))));
        }

        contents.add(Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", userMessage))));

        Map<String, Object> body = new HashMap<>();
        body.put("contents", contents);
        body.put("systemInstruction", Map.of(
                "parts", List.of(Map.of("text", "You are a helpful AI assistant."))));
        body.put("generationConfig", Map.of(
                "temperature", 0.2,
                "maxOutputTokens", 1000));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

            System.out.println("Gemini raw response: " + response.getBody());

            List candidates = (List) response.getBody().get("candidates");
            if (candidates == null || candidates.isEmpty()) {
                throw new RuntimeException("Gemini returned no candidates. Response: " + response.getBody());
            }

            Map firstCandidate = (Map) candidates.get(0);
            String finishReason = (String) firstCandidate.get("finishReason");
            Map content = (Map) firstCandidate.get("content");

            if (content == null) {
                System.err.println("Gemini blocked response. finishReason: " + finishReason);
                return "I'm sorry, I couldn't generate a response for that. Please try rephrasing.";
            }

            List parts = (List) content.get("parts");
            if (parts == null || parts.isEmpty()) {
                return "I'm sorry, I received an empty response. Please try again.";
            }

            Map firstPart = (Map) parts.get(0);
            return firstPart.get("text").toString();

        } catch (HttpClientErrorException e) {
            System.err.println("Gemini HTTP error: " + e.getStatusCode() + " - " + e.getResponseBodyAsString());
            throw new RuntimeException("Gemini API rejected the request: " + e.getResponseBodyAsString());
        } catch (Exception e) {
            System.err.println("Gemini call failed: " + e.getMessage());
            throw new RuntimeException("Failed to reach Gemini API: " + e.getMessage());
        }
    }
}