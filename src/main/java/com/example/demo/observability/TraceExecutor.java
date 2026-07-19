package com.example.demo.observability;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.stereotype.Component;

import java.util.function.Function;
import java.util.function.Supplier;

@Component
public class TraceExecutor {

    private final Tracer tracer;

    public TraceExecutor(Tracer tracer) {
        this.tracer = tracer;
    }

    /**
     * Executes an operation inside a child span.
     *
     * Example:
     * traceExecutor.execute(
     *     "generate-embedding",
     *     () -> embeddingService.generateEmbedding(log)
     * );
     */
    public <T> T execute(String spanName, Supplier<T> operation) {
        return executeWithSpan(spanName, span -> operation.get());
    }

    /**
     * Executes an operation and provides access to the span,
     * allowing custom tags to be added.
     */
    public <T> T executeWithSpan(
            String spanName,
            Function<Span, T> operation
    ) {
        Span span = tracer.nextSpan()
                .name(spanName)
                .start();

        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {

            T result = operation.apply(span);

            span.tag("operation.status", "success");

            return result;

        } catch (RuntimeException exception) {

            span.tag("operation.status", "failure");
            span.tag("error.type", exception.getClass().getSimpleName());
            span.error(exception);

            throw exception;

        } finally {
            span.end();
        }
    }

    /**
     * Use this for methods that return void.
     */
    public void execute(String spanName, Runnable operation) {
        execute(spanName, () -> {
            operation.run();
            return null;
        });
    }
}
