package com.example.demo.service;

import com.example.demo.dto.LogAnalysis;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class LogService {

    @Value("${groq.api.key}")
    private String API_KEY;

    private final RestTemplate restTemplate;
    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;
    private final ObjectMapper objectMapper;

    public LogService(RestTemplate restTemplate,
                      EmbeddingService embeddingService,
                      VectorStoreService vectorStoreService,
                      ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.embeddingService = embeddingService;
        this.vectorStoreService = vectorStoreService;
        this.objectMapper = objectMapper;
    }

    public LogAnalysis analyze(String log) {

        String url = "https://api.groq.com/openai/v1/chat/completions";

        // 🔧 NEW 1: Generate embedding
        List<Double> queryEmbedding = embeddingService.getEmbedding(log);

        // 🔧 NEW 2: Search similar logs
        List<String> similarLogs = vectorStoreService.search(queryEmbedding);
        System.out.println("Similar Logs: " + similarLogs);

        // 🔧 NEW 3: Store current log (for future learning)
        vectorStoreService.store(log, queryEmbedding);

        // 🔧 UPDATED PROMPT (now includes context)
        String prompt = """
        You are a backend expert.

        Here are similar past logs:
        %s

        Now analyze:
        %s

        Return ONLY valid JSON. No explanation, no extra text.

        {
          "rootCause": "...",
          "fix": "...",
          "severity": "LOW | MEDIUM | HIGH"
        }
        """.formatted(similarLogs, log);

        // Request body
        Map<String, Object> body = new HashMap<>();
        body.put("model", "llama-3.1-8b-instant");
        body.put("temperature", 0.3);

        body.put("messages", List.of(
                Map.of("role", "user", "content", prompt)
        ));

        // Headers
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(API_KEY);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> request =
                new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response =
                    restTemplate.postForEntity(url, request, Map.class);

            if (response.getBody() == null) {
                return createError("Empty response from AI");
            }

            JsonNode root = objectMapper.valueToTree(response.getBody());

            JsonNode contentNode = root
                    .path("choices")
                    .path(0)
                    .path("message")
                    .path("content");

            if (contentNode.isMissingNode()) {
                return createError("Invalid AI response structure");
            }

            String content = contentNode.asText();

            int start = content.indexOf("{");
            int end = content.lastIndexOf("}");

            if (start == -1 || end == -1 || start >= end) {
                return createError("Invalid JSON format from AI: " + content);
            }

            String json = content.substring(start, end + 1);

            JsonNode jsonNode = objectMapper.readTree(json);
            LogAnalysis result = objectMapper.treeToValue(jsonNode, LogAnalysis.class);
            result.setLog(log);
            return result;

        } catch (Exception e) {
            return createError("AI parsing failed: " + e.getMessage());
        }
    }

    private LogAnalysis createError(String message) {
        LogAnalysis error = new LogAnalysis();
        error.setRootCause("Error occurred");
        error.setFix(message);
        error.setSeverity("Unknown");
        return error;
    }
}