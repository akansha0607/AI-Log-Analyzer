package com.example.demo.service;

import com.example.demo.dto.LogAnalysis;
import com.example.demo.observability.TraceExecutor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class LogService {

    private static final Logger logger =
            LoggerFactory.getLogger(LogService.class);

    private static final String GROQ_URL =
            "https://api.groq.com/openai/v1/chat/completions";

    private static final String MODEL =
            "llama-3.1-8b-instant";

    @Value("${groq.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate;
    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;
    private final ObjectMapper objectMapper;
    private final TraceExecutor traceExecutor;
    private final Tracer tracer;

    public LogService(
            RestTemplate restTemplate,
            EmbeddingService embeddingService,
            VectorStoreService vectorStoreService,
            ObjectMapper objectMapper,
            TraceExecutor traceExecutor,
            Tracer tracer
    ) {
        this.restTemplate = restTemplate;
        this.embeddingService = embeddingService;
        this.vectorStoreService = vectorStoreService;
        this.objectMapper = objectMapper;
        this.traceExecutor = traceExecutor;
        this.tracer = tracer;
    }

    public LogAnalysis analyze(String log) {

        configureRootSpan(log);

        logger.info("Starting AI log analysis");

        try {
            // 1. Generate embedding
            List<Double> queryEmbedding = traceExecutor.executeWithSpan(
                    "generate-embedding",
                    span -> {
                        span.tag(
                                "opik.metadata.input_length",
                                String.valueOf(log.length())
                        );
                        logger.info("Generating embedding");
                        List<Double> embedding =
                                embeddingService.getEmbedding(log);
                        logger.info(
                                "Generated embedding with {} dimensions",
                                embedding.size()
                        );
                        span.tag(
                                "opik.metadata.embedding_dimensions",
                                String.valueOf(embedding.size())
                        );

                        return embedding;
                    }
            );

            // 2. Retrieve similar logs
            List<String> similarLogs = traceExecutor.executeWithSpan(
                    "vector-search",
                    span -> {
                        logger.info("Searching for similar logs");
                        List<String> results =
                                vectorStoreService.search(queryEmbedding);
                        logger.info(
                                "Vector search found {} results",
                                results.size()
                        );
                        span.tag(
                                "opik.metadata.retrieved_logs_count",
                                String.valueOf(results.size())
                        );

                        return results;
                    }
            );

            logger.info(
                    "Vector search returned {} similar logs",
                    similarLogs.size()
            );

            // 3. Store current log
            traceExecutor.execute(
                    "store-log",
                    () -> vectorStoreService.store(log, queryEmbedding)
            );

            // 4. Build prompt
            String prompt = traceExecutor.executeWithSpan(
                    "build-prompt",
                    span -> {
                        String generatedPrompt =
                                buildPrompt(similarLogs, log);

                        span.tag(
                                "opik.metadata.prompt_length",
                                String.valueOf(generatedPrompt.length())
                        );

                        return generatedPrompt;
                    }
            );

            // 5. Call Groq
            String content = traceExecutor.executeWithSpan(
                    "groq-llm-call",
                    span -> callGroq(prompt, span)
            );

            // 6. Parse response
            LogAnalysis result = traceExecutor.executeWithSpan(
                    "parse-llm-response",
                    span -> parseResponse(content, log, span)
            );

            logger.info(
                    "AI log analysis completed with severity={}",
                    result.getSeverity()
            );

            return result;

        } catch (Exception exception) {
            logger.error("AI log analysis failed", exception);

            Span currentSpan = tracer.currentSpan();

            if (currentSpan != null) {
                currentSpan.error(exception);
                currentSpan.tag("operation.status", "failure");
                currentSpan.tag(
                        "error.type",
                        exception.getClass().getSimpleName()
                );
            }

            return createError(
                    "AI analysis failed: " + exception.getMessage(),
                    log
            );
        }
    }

    private void configureRootSpan(String inputLog) {
        Span currentSpan = tracer.currentSpan();

        if (currentSpan == null) {
            return;
        }

        currentSpan.name("analyze-log");

        currentSpan.tag(
                "opik.tags",
                "ai-log-analyzer,rag,groq"
        );

        currentSpan.tag(
                "opik.metadata.application",
                "ai-log-analyzer"
        );

        currentSpan.tag(
                "opik.metadata.pipeline",
                "log-analysis-rag"
        );

        currentSpan.tag(
                "opik.metadata.input_length",
                String.valueOf(inputLog.length())
        );
    }

    private String buildPrompt(
            List<String> similarLogs,
            String currentLog
    ) {
        return """
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
                """.formatted(similarLogs, currentLog);
    }

    private String callGroq(String prompt, Span span) {

        span.tag("gen_ai.system", "groq");
        span.tag("gen_ai.operation.name", "chat");
        span.tag("gen_ai.request.model", MODEL);
        span.tag("opik.metadata.temperature", "0.3");

        Map<String, Object> body = new HashMap<>();
        body.put("model", MODEL);
        body.put("temperature", 0.3);

        body.put(
                "messages",
                List.of(
                        Map.of(
                                "role", "user",
                                "content", prompt
                        )
                )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> request =
                new HttpEntity<>(body, headers);

        long startTime = System.currentTimeMillis();

        ResponseEntity<Map> response =
                restTemplate.postForEntity(
                        GROQ_URL,
                        request,
                        Map.class
                );

        long latency =
                System.currentTimeMillis() - startTime;

        span.tag(
                "opik.metadata.llm_latency_ms",
                String.valueOf(latency)
        );

        span.tag(
                "http.response.status_code",
                String.valueOf(response.getStatusCode().value())
        );

        if (response.getBody() == null) {
            throw new IllegalStateException(
                    "Empty response received from Groq"
            );
        }

        JsonNode root =
                objectMapper.valueToTree(response.getBody());

        JsonNode contentNode = root
                .path("choices")
                .path(0)
                .path("message")
                .path("content");

        if (contentNode.isMissingNode() || contentNode.isNull()) {
            throw new IllegalStateException(
                    "Invalid Groq response structure"
            );
        }

        String content = contentNode.asText();

        span.tag(
                "opik.metadata.response_length",
                String.valueOf(content.length())
        );

        return content;
    }

    private LogAnalysis parseResponse(
            String content,
            String originalLog,
            Span span
    ) {
        try {
            int start = content.indexOf("{");
            int end = content.lastIndexOf("}");

            if (start == -1 || end == -1 || start >= end) {
                throw new IllegalStateException(
                        "No valid JSON object found in AI response"
                );
            }

            String json = content.substring(start, end + 1);

            JsonNode jsonNode = objectMapper.readTree(json);

            LogAnalysis result =
                    objectMapper.treeToValue(
                            jsonNode,
                            LogAnalysis.class
                    );

            result.setLog(originalLog);

            span.tag(
                    "opik.metadata.severity",
                    result.getSeverity()
            );

            return result;

        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Unable to parse Groq response",
                    exception
            );
        }
    }

    private LogAnalysis createError(
            String message,
            String originalLog
    ) {
        LogAnalysis error = new LogAnalysis();
        error.setRootCause("Error occurred");
        error.setFix(message);
        error.setSeverity("UNKNOWN");
        error.setLog(originalLog);

        return error;
    }
}