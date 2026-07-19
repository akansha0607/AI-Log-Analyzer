package com.example.demo.observability;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class TraceIdFilter extends OncePerRequestFilter {

    private final Tracer tracer;

    public TraceIdFilter(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        Span currentSpan = tracer.currentSpan();

        if (currentSpan == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String traceId = currentSpan.context().traceId();
        String spanId = currentSpan.context().spanId();

        try {
            MDC.put("traceId", traceId);
            MDC.put("spanId", spanId);

            // The caller can use this ID to locate the request in Opik.
            response.setHeader("X-Trace-Id", traceId);

            filterChain.doFilter(request, response);

        } finally {
            MDC.remove("traceId");
            MDC.remove("spanId");
        }
    }
}
