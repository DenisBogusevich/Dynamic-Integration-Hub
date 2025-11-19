package org.example.registry;

import org.example.exception.StepTypeNotFoundException;
import org.example.step.PipelineStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core Registry: Maps symbolic alias strings (from JSON) to concrete Java implementation classes.
 * <p>
 * This registry acts as a <b>Security Allow-list</b>, ensuring that only explicitly registered
 * classes can be instantiated by the pipeline engine.
 * </p>
 */
@Component
public class StepTypeRegistry {

    private static final Logger log = LoggerFactory.getLogger(StepTypeRegistry.class);

    // Using Wildcard with upper bound to enforce type safety at the storage level
    private final Map<String, Class<? extends PipelineStep<?, ?>>> stepMap = new ConcurrentHashMap<>();

    /**
     * Manually registers a new step type.
     *
     * @param type  The unique symbolic alias (e.g., "JdbcSource").
     * @param clazz The implementation class.
     * @deprecated <b>Architectural Debt:</b> Manual registration is error-prone and boilerplate-heavy.
     * <br><b>Recommendation:</b> Implement an annotation-based auto-discovery mechanism (e.g., {@code @DihStep("alias")})
     * using Spring's {@code ClassPathScanningCandidateComponentProvider} or a {@code BeanPostProcessor} to automatically
     * populate this registry on startup.
     */
    @Deprecated(forRemoval = false) // Keeping it for testing/overrides, but discouraging standard use.
    public void register(String type, Class<?> clazz) {
        // 1. Safety Check: Ensure the class actually implements the required interface
        if (!PipelineStep.class.isAssignableFrom(clazz)) {
            throw new IllegalArgumentException(
                    "Security Error: Class '" + clazz.getName() + "' does not implement the PipelineStep interface."
            );
        }

        // 2. Overwrite Warning: Prevent silent shadowing of existing steps
        if (stepMap.containsKey(type)) {
            Class<?> existing = stepMap.get(type);
            log.warn("Collision detected! Overwriting step type '{}'. Previous: {}, New: {}",
                    type, existing.getName(), clazz.getName());
        }

        // 3. Safe Cast and Store
        // We checked isAssignableFrom above, so this unchecked cast is safe.
        @SuppressWarnings("unchecked")
        Class<? extends PipelineStep<?, ?>> castedClass = (Class<? extends PipelineStep<?, ?>>) clazz;
        stepMap.put(type, castedClass);

        log.debug("Registered step type '{}' -> {}", type, clazz.getSimpleName());
    }

    /**
     * Registers a step type.
     * Ideally called only by infrastructure components (like StepRegistryBeanPostProcessor).
     *
     * @param type  The unique symbolic alias.
     * @param clazz The implementation class.
     */
    public void registerStep(String type, Class<? extends PipelineStep<?, ?>> clazz) {
        if (stepMap.containsKey(type)) {
            Class<?> existing = stepMap.get(type);
            log.warn("Collision detected! Overwriting step type '{}'. Previous: {}, New: {}",
                    type, existing.getName(), clazz.getName());
        }

        stepMap.put(type, clazz);
        log.info("Registered DIH Step: '{}' -> {}", type, clazz.getName());
    }


    /**
     * Resolves the Java class for a given symbolic type.
     *
     * @param type The alias string from the Pipeline Definition.
     * @return The concrete Java class.
     * @throws StepTypeNotFoundException if the type is unknown (Fail-Fast).
     */
    public Class<? extends PipelineStep<?, ?>> getStepClass(String type) {
        var clazz = stepMap.get(type);
        if (clazz == null) {
            log.error("Registry lookup failed for type: '{}'. Available types: {}", type, stepMap.keySet());
            throw new StepTypeNotFoundException(type);
        }
        return clazz;
    }

    public Map<String, String> getRegisteredSteps() {
        Map<String, String> result = new ConcurrentHashMap<>();
        stepMap.forEach((k, v) -> result.put(k, v.getSimpleName()));
        return result;
    }
}