package com.example.demo.controller;

import com.example.demo.dto.LogAnalysis;
import com.example.demo.service.LogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/logs")
public class LogController {

    @Autowired
    private LogService logService;

    @PostMapping("/analyze")
    public LogAnalysis analyzeLog(@RequestBody Map<String, String> request) {
        String log = request.get("log");
        return logService.analyze(log);
    }
}