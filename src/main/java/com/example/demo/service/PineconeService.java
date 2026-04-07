package com.example.demo.service;

import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class PineconeService {

    public List<String> search(String log) {
        // Dummy data for now (so your app runs)
        return List.of(
                "NullPointerException in UserService",
                "Database connection timeout",
                "Kafka consumer failed"
        );
    }
}