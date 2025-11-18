package org.example.step;

/**
 * Encapsulates all dynamic, runtime-specific metadata relevant to the
 * current pipeline execution thread.
 * This object is passed to every PipelineStep's execute method.
 *
 * @param executionId The unique identifier for the current synchronous pipeline run.
 * @param startTime The timestamp when the current pipeline run was initialized.
 * // Add more fields as needed, e.g., threadName, tenantId, etc.
 */
public record PipelineContext(
        String executionId,
        long startTime
) {
    // Note: The executionId can be retrieved from PipelineContextHolder,
    // but passing it explicitly makes the execute method's signature clearer
    // and more testable.
}