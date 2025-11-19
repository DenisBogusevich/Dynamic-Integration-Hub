package org.example.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.example.exception.DihCoreException;
import org.example.exception.PipelineConfigurationException;
import org.example.exception.StepExecutionException;
import org.example.model.PipelineDefinition;
import org.example.model.StepDefinition;
import org.example.scope.PipelineContextHolder;
import org.example.step.PipelineContext;
import org.example.step.PipelineStep; // Предполагаем, что DIH-101 завершена
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * The core execution orchestrator for dynamic pipelines.
 * Manages the lifecycle of a single pipeline run, ensuring context isolation
 * and sequential execution of registered steps.
 */
@Service
public class PipelineExecutor {

    private final ApplicationContext context;
    private final MeterRegistry meterRegistry;

    @Autowired
    public PipelineExecutor(ApplicationContext context, MeterRegistry meterRegistry) {
        this.context = context;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Executes the pipeline defined by the configuration.
     *
     * @param definition The blueprint of the pipeline to run.
     * @return The final result produced by the last step.
     */
    public Object executePipeline(PipelineDefinition definition) {
        Timer.Sample sample = Timer.start(meterRegistry);
        // 1. Управление контекстом (Execution Context Management)
        String pipelineName = definition.name();
        String executionId = UUID.randomUUID().toString();
        long startTime = Instant.now().toEpochMilli();
        Object currentData = null; // Данные, передаваемые между шагами

        // Context object to pass to the step's execute method.
        PipelineContext pipelineContext = new PipelineContext(executionId, startTime,pipelineName);

        // Critical: Initialize ThreadLocal context.
        PipelineContextHolder.initializeContext(pipelineContext);

        String status = "success";

        String stepId = "";
        try {

            System.out.println("Starting Pipeline '" + definition.name() + "' (ID: " + executionId + ")");

            // 2. Итерация и выполнение шагов (Step Iteration and Execution)
            for (StepDefinition stepDef : definition.steps()) {
                stepId = stepDef.id();
                String beanName = definition.name() + "_" + stepDef.id();

                // 2.1. Получение бина из Spring Context
                // Поскольку мы не знаем конкретные Generics I, O, мы используем Object
                // и приводим к общему контракту PipelineStep.
                Object stepBean = context.getBean(beanName);

                // 2.2. Проверка типа (Defensive Programming)
                if (!(stepBean instanceof PipelineStep)) {
                    throw new IllegalStateException("Bean '" + beanName +
                            "' is not a PipelineStep. Check StepTypeRegistry registration.");
                }

                // Безопасное приведение типа для исполнения
                PipelineStep<Object, Object> step = (PipelineStep<Object, Object>) stepBean;

                System.out.println("Executing step: " + beanName + " with input: " + (currentData != null ? currentData.getClass().getSimpleName() : "null"));

                // 2.3. Выполнение шага и передача результата
                currentData = step.execute(currentData, pipelineContext);
            }

            System.out.println("Pipeline '" + definition.name() + "' finished successfully.");
            return currentData;

        } catch (NoSuchBeanDefinitionException e) {
            status = "missing_step";
            // Обертываем ошибку регистрации в PipelineConfigurationException
            throw new PipelineConfigurationException(
                    "Execution failure due to missing step registration: " + e.getMessage(), e); // NEW!

        } catch (DihCoreException e) {
            // Ловим наши кастомные ошибки (StepExecutionException, PipelineConcurrencyException, и т.д.)
            status = "failure";
            // Просто пробрасываем их дальше, они уже содержат нужную информацию
            throw e; // NEW!

        } catch (Exception e) {
            status = "failure";
            // Ловим любые другие (unchecked или checked) исключения от шага и оборачиваем их.

            // Оборачиваем в StepExecutionException для унификации обработки ошибок исполнения
            throw new StepExecutionException(
                    "Uncaught exception during step execution.", stepId, e); // NEW!

        }finally {
            // Critical: Cleanup ThreadLocal context to prevent memory leaks and state pollution.
            PipelineContextHolder.cleanup();

            sample.stop(Timer.builder("dih.pipeline.execution") // Имя метрики
                    .tag("pipeline.name", definition.name())
                    .tag("execution.status", status)
                    .description("Measures the total execution time of a pipeline")
                    .register(meterRegistry));
        }
    }
}