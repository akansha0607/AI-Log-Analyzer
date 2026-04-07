package com.example.demo.service;

import com.example.demo.entity.LogEntity;
import com.example.demo.repository.LogRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;


@Service
public class VectorStoreService {

    private final LogRepository logRepository;

    private final ObjectMapper objectMapper;

    public VectorStoreService(LogRepository logRepository, ObjectMapper objectMapper) {
        this.logRepository = logRepository;
        this.objectMapper = objectMapper;
    }

    public void store(String log, List<Double> embedding) {
        boolean exists = logRepository.findAll().stream()
                .anyMatch(entry -> entry.getLog().equals(log));

        if (!exists) {
            LogEntity entity = new LogEntity();
            entity.setLog(log);
            entity.setEmbedding(toJson(embedding));
            logRepository.save(entity);
        }
        System.out.println("DB size: " + logRepository.count());
    }

    public List<String> search(List<Double> queryEmbedding) {
        List<LogEntity> logs = logRepository.findAll();
        return logs.stream()
                .sorted((a, b) -> Double.compare(
                        cosineSimilarity(b.getEmbedding(), queryEmbedding),
                        cosineSimilarity(a.getEmbedding(), queryEmbedding)
                ))
                .limit(3)
                .map(LogEntity::getLog)
                .toList();
    }

    private double cosineSimilarity(String v1Json, List<Double> v2) {
        double dot = 0, norm1 = 0, norm2 = 0;

        List<Double> v1 = fromJson(v1Json);
        int size = Math.min(v1.size(), v2.size());

        for (int i = 0; i < size; i++) {
            dot += v1.get(i) * v2.get(i);
            norm1 += v1.get(i) * v1.get(i);
            norm2 += v2.get(i) * v2.get(i);
        }

        return dot / (Math.sqrt(norm1) * Math.sqrt(norm2) + 1e-10);
    }

    private String toJson(List<Double> embedding) {
        try {
            return objectMapper.writeValueAsString(embedding);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private List<Double> fromJson(String json) {
        System.out.println("JSON coming: " + json);
        try {
            json = json.replace("{", "[").replace("}", "]");
            JsonNode node = objectMapper.readTree(json);
            if (node.isArray()) {
                return objectMapper.convertValue(
                        node,
                        objectMapper.getTypeFactory()
                                .constructCollectionType(List.class, Double.class)
                );
            }

            if (node.isObject() && node.has("embedding")) {
                return objectMapper.convertValue(
                        node.get("embedding"),
                        objectMapper.getTypeFactory()
                                .constructCollectionType(List.class, Double.class)
                );
            }

            throw new RuntimeException("Invalid JSON format for embedding");

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}