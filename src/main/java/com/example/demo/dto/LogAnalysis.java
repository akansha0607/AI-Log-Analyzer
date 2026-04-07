package com.example.demo.dto;

import lombok.Data;

@Data
public class LogAnalysis {
    private String log;
    private String rootCause;
    private String fix;
    private String severity;
}
