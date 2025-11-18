package org.example.service;

import org.example.model.PipelineDefinition;
import org.example.model.StepDefinition;
import org.example.registry.StepTypeRegistry;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.stereotype.Service;

@Service
public class PipelineRegistrar {

    private final StepTypeRegistry stepTypeRegistry;
    private final GenericApplicationContext genericApplicationContext;

    public PipelineRegistrar(StepTypeRegistry stepTypeRegistry, GenericApplicationContext genericApplicationContext) {
        this.stepTypeRegistry = stepTypeRegistry;
        this.genericApplicationContext = genericApplicationContext;
    }

    /**
     * Registers all steps defined in the PipelineDefinition, ensuring unique bean naming.
     * This is the main entry point for dynamic pipeline deployment.
     *
     * @param definition The complete pipeline configuration DTO.
     */
    public void registerPipeline(PipelineDefinition definition) {
        // 1. Создаем уникальный префикс для всех бинов этого пайплайна
        String pipelineName = definition.name();

        if (definition.steps() == null || definition.steps().isEmpty()) {
            System.out.println("Pipeline " + pipelineName + " has no steps defined. Registration skipped.");
            return;
        }

        // 2. Итеративно регистрируем каждый шаг
        for (StepDefinition stepDef : definition.steps()) {
            registerStepRecursive(pipelineName, stepDef);
        }

        System.out.println("Pipeline '" + pipelineName + "' and all steps registered successfully.");
    }

    /**
     * Registers a single step as a Spring Bean.
     *
     * @param pipelineName Name of the pipeline (namespace prefix).
     * @param stepDefinition      The step configuration DTO.
     */
    public void registerStep(String pipelineName, StepDefinition stepDefinition) {
        Class<?> stepClass = stepTypeRegistry.getStepClass(stepDefinition.type());

        BeanDefinitionBuilder stepDefinitionBuilder = BeanDefinitionBuilder.genericBeanDefinition(stepClass);

        if(stepDefinition.properties() != null) {
            stepDefinition.properties().forEach(stepDefinitionBuilder::addPropertyValue);
        }

        // *** КРИТИЧЕСКОЕ ДОБАВЛЕНИЕ: СОХРАНЕНИЕ МЕТАДАННЫХ ***
        if (stepDefinition.retryPolicy() != null) {
            // Используем BeanDefinition.setAttribute для хранения кастомной информации
            stepDefinitionBuilder.getRawBeanDefinition().setAttribute(
                    "retryPolicy", stepDefinition.retryPolicy());
        }

        stepDefinitionBuilder.setScope("pipeline");

        String beanName = pipelineName + "_" + stepDefinition.id();

        genericApplicationContext.registerBeanDefinition(beanName, stepDefinitionBuilder.getBeanDefinition());
        System.out.println("Registered bean: " + beanName);
    }

    private void registerStepRecursive(String pipelineName, StepDefinition stepDef) {
        registerStep(pipelineName, stepDef);

        if (stepDef.subSteps() != null && !stepDef.subSteps().isEmpty()) {
            System.out.println("Found sub-steps in " + stepDef.id() + ". Registering branches.");

            for (StepDefinition subStepDef : stepDef.subSteps()) {
                // Примечание: Для вложенных шагов можно создать более длинный префикс
                // (e.g., pipelineName_parentStepId) для дополнительной изоляции
                registerStepRecursive(pipelineName, subStepDef);
            }
        }
    }

}
