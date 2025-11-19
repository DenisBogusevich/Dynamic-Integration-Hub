package org.example.service;

import org.example.model.PipelineDefinition;
import org.example.model.StepDefinition;
import org.example.registry.StepTypeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.stereotype.Service;

/**
 * Responsible for translating the high-level {@link PipelineDefinition} (JSON)
 * into low-level Spring {@link BeanDefinition}s.
 * <p>
 * This component is stateless and purely functional. It populates a provided
 * {@link BeanDefinitionRegistry} (usually an ephemeral child context) with
 * the step definitions required for a specific pipeline run.
 * </p>
 */
@Service
public class PipelineRegistrar {

    private static final Logger log = LoggerFactory.getLogger(PipelineRegistrar.class);

    private final StepTypeRegistry stepTypeRegistry;

    public PipelineRegistrar(StepTypeRegistry stepTypeRegistry) {
        this.stepTypeRegistry = stepTypeRegistry;
    }

    /**
     * Registers all steps from the definition into the provided registry.
     *
     * @param definition The pipeline blueprint.
     * @param registry   The target Spring registry (typically a Child Context).
     */
    public void registerPipeline(PipelineDefinition definition, BeanDefinitionRegistry registry) {
        String pipelineName = definition.name();

        if (definition.steps() == null || definition.steps().isEmpty()) {
            log.warn("Pipeline '{}' has no steps defined. Skipping registration.", pipelineName);
            return;
        }

        // Recursively register steps to handle composite structures (if any)
        for (StepDefinition stepDef : definition.steps()) {
            registerStepRecursive(pipelineName, stepDef, registry);
        }

        log.debug("Successfully registered pipeline '{}' with {} top-level steps.",
                pipelineName, definition.steps().size());
    }

    /**
     * Registers a single step definition as a Spring Bean.
     *
     * @param pipelineName The namespace prefix.
     * @param stepDefinition The step configuration.
     * @param registry     The target registry.
     */
    public void registerStep(String pipelineName, StepDefinition stepDefinition, BeanDefinitionRegistry registry) {
        // 1. Resolve the implementation class
        Class<?> stepClass = stepTypeRegistry.getStepClass(stepDefinition.type());

        // 2. Build the BeanDefinition
        BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(stepClass);

        // 3. Bind simple properties (Setters)
        if (stepDefinition.properties() != null) {
            stepDefinition.properties().forEach(builder::addPropertyValue);
        }

        // 4. Attach Metadata for Post-Processors (Critical for Retry AOP)
        if (stepDefinition.retryPolicy() != null) {
            builder.getRawBeanDefinition().setAttribute("retryPolicy", stepDefinition.retryPolicy());
        }

        // 5. Scope: SINGLETON is correct here because the entire Context is ephemeral (scoped to the request).
        builder.setScope(BeanDefinition.SCOPE_SINGLETON);

        // 6. Generate unique bean name
        String beanName = pipelineName + "_" + stepDefinition.id();

        // 7. Register
        registry.registerBeanDefinition(beanName, builder.getBeanDefinition());
        log.trace("Registered bean definition: {}", beanName);
    }

    private void registerStepRecursive(String pipelineName, StepDefinition stepDef, BeanDefinitionRegistry registry) {
        registerStep(pipelineName, stepDef, registry);

        // Support for nested steps (e.g., for ParallelSplitter branches)
        if (stepDef.subSteps() != null) {
            for (StepDefinition subStepDef : stepDef.subSteps()) {
                registerStepRecursive(pipelineName, subStepDef, registry);
            }
        }
    }
}