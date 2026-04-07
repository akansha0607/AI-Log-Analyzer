package com.example.demo.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class EmbeddingService {

    public List<Double> getEmbedding(String text) {
        List<Double> vector = new ArrayList<>();

        if (text == null) return vector;

        // Simple mock embedding (character-based)
        for (char c : text.toCharArray()) {
            vector.add((double) c / 100);
        }

        return vector;
    }
}