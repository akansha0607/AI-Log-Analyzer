package com.example.demo.dto;

import java.util.List;

public record StoredLog(String log, List<Double> embedding) {}
