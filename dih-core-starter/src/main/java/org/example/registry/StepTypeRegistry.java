package org.example.registry;

import org.example.exception.StepTypeNotFoundException;
import org.example.step.PipelineStep;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry that maps symbolic step type aliases (from JSON) to actual Java classes.
 * This acts as a security allow-list and decouples configuration from implementation.
 */
@Component
public class StepTypeRegistry {

    private final Map<String, Class<?>> stepMap = new ConcurrentHashMap<>();

    /**
     * Registers a new step type.
     *
     * @param type  The alias used in JSON configuration (e.g., "JdbcSource").
     * @param clazz The actual Java class that implements this step.
     */
    public void register(String type, Class<?> clazz) {

        if(!PipelineStep.class.isAssignableFrom(clazz)) {
            throw new IllegalArgumentException(
                    "Class '" + clazz.getName() + "' must implement the PipelineStep interface."
            );
        }

        // Можно добавить валидацию, например, проверять, что clazz реализует интерфейс PipelineStep
        if (stepMap.containsKey(type)) {
            // Логируем предупреждение о перезаписи, если нужно
        }
        stepMap.put(type, clazz);
    }

    /**
     * Resolves a Java class by its symbolic type alias.
     *
     * @param type The alias from JSON.
     * @return The corresponding Java class.
     * @throws StepTypeNotFoundException if the alias is not registered.
     */
    public Class<?> getStepClass(String type) {
        Class<?> clazz = stepMap.get(type);
        if (clazz == null) {
            throw new StepTypeNotFoundException(type);
        }
        return clazz;
    }
}