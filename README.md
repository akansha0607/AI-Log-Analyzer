# 🚀 AI Log Analyzer (Java + GenAI + RAG)

## Overview
The **AI Log Analyzer** is a Java-based backend system that leverages **Generative AI (LLM) and Retrieval-Augmented Generation (RAG)** to intelligently analyze application logs.  

It helps developers quickly identify **root causes**, suggest **fixes**, and determine **severity**, significantly reducing debugging time and manual effort.

---

## Features

### 🤖 AI-Powered Log Analysis
- Analyze logs using LLM to generate:
  - Root Cause
  - Suggested Fix
  - Severity Level  

### 🔍 RAG-Based Retrieval
- Converts logs into embeddings  
- Retrieves similar past logs using **cosine similarity**  
- Provides context-aware responses  

### 🧠 Intelligent Context Building
- Combines current log + historical logs  
- Improves accuracy and reduces hallucination  

### ⚡ Backend-Driven GenAI System
- Fully implemented using **Java + Spring Boot**  
- No dependency on Python-based pipelines  

### 📊 Structured Output
- Ensures LLM responses are returned in strict **JSON format**  
- Easily consumable by backend systems  

### 🔭 MDC & OpenTelemetry Tracing
- Adds unique traceId and spanId values to application logs using MDC
- Creates spans for each stage of the RAG pipeline
- Exports traces to Opik for latency and failure monitoring
---

## ⚙️ How It Works (Architecture)
Client Request (/logs/analyze)
↓
Log Input
↓
Embedding Generation
↓
PostgreSQL (store + retrieve embeddings)
↓
Cosine Similarity Search
↓
Retrieve Similar Logs (RAG)
↓
LLM (Groq API - LLaMA)
↓
Structured JSON Response


---

## 🛠 Tech Stack

[![Java](https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://www.java.com/)  
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-6DB33F?style=for-the-badge&logo=spring&logoColor=white)](https://spring.io/projects/spring-boot)  
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-316192?style=for-the-badge&logo=postgresql&logoColor=white)](https://www.postgresql.org/)  
[![Groq API](https://img.shields.io/badge/LLM-Groq_API-blue?style=for-the-badge)](https://groq.com/)  
[![Maven](https://img.shields.io/badge/Maven-C71A36?style=for-the-badge&logo=apachemaven&logoColor=white)](https://maven.apache.org/)

---

## Installation

## ✅ Prerequisites

Ensure the following are installed:

`Java 17+` | `PostgreSQL` | `Maven`
---

### Backend Setup

```sh
# Clone the repository
git clone https://github.com/akansha0607/AI-Log-Analyzer.git
cd AI-Log-Analyzer

# Build and run
mvn clean install
mvn spring-boot:run
```

---
## 📡 API Endpoints

| Endpoint        | Method | Description                                      |
|----------------|--------|--------------------------------------------------|
| `/logs/analyze` | POST   | Analyze a log and get AI-generated insights      |
