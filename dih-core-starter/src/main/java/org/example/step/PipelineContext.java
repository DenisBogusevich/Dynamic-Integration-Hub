package org.example.step;

import org.springframework.util.Assert;

/**
 * Immutable value object representing the <b>Runtime Metadata</b> of a single pipeline execution.
 * <p>
 * This object acts as the "Context" passed explicitly to every {@link PipelineStep#execute} method.
 * It ensures that steps remain stateless and decoupled from the framework's internal storage mechanisms
 * (like ThreadLocals).
 * </p>
 *
 * <h2>Design Philosophy:</h2>
 * <ul>
 * <li><b>Explicit Dependencies:</b> Steps receive their context as an argument, making unit testing trivial.</li>
 * <li><b>Immutability:</b> As a Java Record, it is thread-safe by definition, which is critical for parallel branches.</li>
 * <li><b>Observability:</b> Carries the 'Correlation ID' (executionId) required for distributed tracing.</li>
 * </ul>
 *
 * @param executionId  The unique UUID for this specific run (Correlation ID).
 * @param startTime    The epoch timestamp (ms) when the pipeline started.
 * @param pipelineName The human-readable name of the pipeline definition.
 */
public record PipelineContext(
        String executionId,
        long startTime,
        String pipelineName
) {

    /**
     * Compact constructor for validation.
     * Prevents the creation of a "headless" context.
     */
    public PipelineContext {
        Assert.hasText(executionId, "Execution ID must not be null or empty");
        Assert.hasText(pipelineName, "Pipeline Name must not be null or empty");
        if (startTime <= 0) {
            throw new IllegalArgumentException("Start Time must be positive");
        }
    }

    /**
     * Utility to calculate the elapsed time since the pipeline started.
     * Useful for logging or timeout logic within steps.
     *
     * @return Duration in milliseconds.
     */
    public long getElapsedTime() {
        return System.currentTimeMillis() - startTime;
    }

    // Architectural Note:
    // While PipelineContextHolder exists for MDC (logging) and infrastructure (TaskDecorators),
    // Business Logic inside steps should ALWAYS prefer using this 'context' argument
    // rather than calling static methods.
}