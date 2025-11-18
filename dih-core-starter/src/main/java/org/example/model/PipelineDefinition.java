package org.example.model;

import java.util.List;

/**
 * Represents the blueprint of an entire data integration process.
 * This structure is deserialized directly from the JSON/YAML configuration.
 *
 * @param name    The unique identifier of the pipeline (e.g., "OrderProcessing").
 * Used for logging and potential metrics tagging.
 * @param scope   The lifecycle scope of the pipeline components.
 * Defaults to "pipeline" (custom scope implementation).
 * @param version The version of the pipeline definition (e.g., "1.0.0").
 * Useful for configuration management and blue-green deployments.
 * @param steps   The ordered list of processing steps to be executed.
 */
public record PipelineDefinition(
        String name,
        String scope,
        String version,
        List<StepDefinition> steps
) {
}
