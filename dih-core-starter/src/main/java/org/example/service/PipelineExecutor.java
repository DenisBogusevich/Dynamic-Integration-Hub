package org.example.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.example.aop.RetryBeanPostProcessor;
import org.example.bpp.DynamicContextBeanPostProcessor;
import org.example.exception.DihCoreException;
import org.example.exception.PipelineConfigurationException;
import org.example.exception.StepExecutionException;
import org.example.model.PipelineDefinition;
import org.example.model.StepDefinition;
import org.example.scope.PipelineContextHolder;
import org.example.step.PipelineContext;
import org.example.step.PipelineStep; // Предполагаем, что DIH-101 завершена
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
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

    private static final Logger log = LoggerFactory.getLogger(PipelineExecutor.class);

    private final ApplicationContext parentContext;
    private final MeterRegistry meterRegistry;
    private final PipelineRegistrar registrar;

    @Autowired
    public PipelineExecutor(ApplicationContext parentContext,
                            MeterRegistry meterRegistry,
                            PipelineRegistrar registrar) {
        this.parentContext = parentContext;
        this.meterRegistry = meterRegistry;
        this.registrar = registrar;
    }

    /**
     * Executes the pipeline defined by the configuration.
     *
     * @param definition The blueprint of the pipeline to run.
     * @return The final result produced by the last step.
     */
    public Object executePipeline(PipelineDefinition definition) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String executionId = UUID.randomUUID().toString();

        PipelineContext pipelineContext = new PipelineContext(executionId, Instant.now().toEpochMilli(), definition.name());
        PipelineContextHolder.initializeContext(pipelineContext);

        try (var childContext = new AnnotationConfigApplicationContext()) {

            // 2.1 Настраиваем иерархию
            childContext.setParent(parentContext);
            childContext.setDisplayName("Child-Pipeline-" + executionId);

            // 2.2 Регистрируем Инфраструктуру ВНУТРИ ребенка
            // Чтобы @RetryableStep и @InjectDynamicContext работали для бинов этого пайплайна
            childContext.registerBean(RetryBeanPostProcessor.class);
            childContext.registerBean(DynamicContextBeanPostProcessor.class);

            // 2.3 Регистрируем шаги пайплайна через Registrar
            // Передаем childContext как реестр
            registrar.registerPipeline(definition, childContext);

            // 2.4 Поднимаем контекст (Refresh) — в этот момент создаются синглтоны и применяются прокси
            childContext.refresh();

            log.info("Pipeline '{}' started. Child context created: {}", definition.name(), childContext.getId());

            // 3. Выполнение (Execution)
            Object currentData = null;
            for (StepDefinition stepDef : definition.steps()) {
                String beanName = definition.name() + "_" + stepDef.id();

                // Берем бин из CHILD context
                Object stepBean = childContext.getBean(beanName);

                if (!(stepBean instanceof PipelineStep)) {
                    throw new IllegalStateException("Bean is not a PipelineStep: " + beanName);
                }

                PipelineStep<Object, Object> step = (PipelineStep<Object, Object>) stepBean;
                currentData = step.execute(currentData, pipelineContext);
            }

            return currentData;

        } catch (DihCoreException e) {
            // [ИСПРАВЛЕНИЕ]
            // Если ошибка уже "наша" (Concurrency, StepExecution, RetryExhausted),
            // мы не должны её прятать. Пробрасываем выше.
            log.error("Pipeline execution failed with domain error: {}", e.getMessage());
            throw e;

        } catch (Exception e) {
            // А вот все остальные (Spring Beans exceptions, NullPointer, etc)
            // оборачиваем в ConfigurationException, так как это скорее всего инфраструктурный сбой.
            log.error("Pipeline execution failed with unexpected error", e);
            throw new PipelineConfigurationException("Execution failed: " + e.getMessage(), e);

        } finally {
            // Очистка ThreadLocal (MDC)
            PipelineContextHolder.cleanup();

            sample.stop(Timer.builder("dih.pipeline.execution")
                    .tag("pipeline.name", definition.name())
                    .register(meterRegistry));

            // childContext.close() вызовется автоматически благодаря try-with-resources
            log.debug("Child context closed. All temporary beans destroyed.");
        }


        /*String status = "success";

        String stepId = "";
        try {

            log.info("Starting Pipeline '{}'", definition.name());
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

                log.debug("Executing step: {} with input type: {}",
                        beanName, (currentData != null ? currentData.getClass().getSimpleName() : "null"));
                // 2.3. Выполнение шага и передача результата
                currentData = step.execute(currentData, pipelineContext);
            }

            log.info("Pipeline '{}' finished successfully.", definition.name());
            return currentData;

        } catch (NoSuchBeanDefinitionException e) {
            status = "missing_step";
            // Обертываем ошибку регистрации в PipelineConfigurationException
            log.error("Configuration error: Step not found.", e);
            throw new PipelineConfigurationException(
                    "Execution failure due to missing step registration: " + e.getMessage(), e); // NEW!

        } catch (DihCoreException e) {
            // Ловим наши кастомные ошибки (StepExecutionException, PipelineConcurrencyException, и т.д.)
            status = "failure";
            log.error("Pipeline execution failed.", e); // MDC покажет ID, стек-трейс покажет причину
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
        }*/
    }
}