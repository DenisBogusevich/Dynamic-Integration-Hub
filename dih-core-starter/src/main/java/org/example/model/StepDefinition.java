package org.example.model;

import java.util.List;
import java.util.Map;

/**
 * Configuration for a single processing unit within the pipeline.
 * Maps to a specific Spring Bean definition at runtime.
 *
 * @param id          Unique identifier of the step within the pipeline (e.g., "read-sql").
 * Used to generate the bean name.
 * @param type        The symbolic alias of the component type (e.g., "JdbcSource", "ParallelSplitter").
 * This must be registered in the {@code StepTypeRegistry}.
 * @param properties  Key-value pairs to be injected into the component instance.
 * Can contain simple values, maps, or lists.
 * Keys must match the setter methods or constructor arguments of the target class.
 * @param subSteps    Nested steps for composite components (like ParallelSplitter).
 * Allows creating tree-like execution structures.
 * @param retryPolicy Configuration for fault tolerance mechanism (AOP).
 * If null, no retry logic will be applied.
 */
public record StepDefinition(
        String id,
        String type,
        Map<String,Object> properties,
        List<StepDefinition> subSteps,
        RetryPolicyDefinition retryPolicy
) {
}
