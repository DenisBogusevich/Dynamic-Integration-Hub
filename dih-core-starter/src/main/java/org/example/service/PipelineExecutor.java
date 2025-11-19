package org.example.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.example.aop.RetryBeanPostProcessor;
import org.example.bpp.DynamicContextBeanPostProcessor;
import org.example.exception.DihCoreException;
import org.example.exception.PipelineConfigurationException;
import org.example.model.PipelineDefinition;
import org.example.model.StepDefinition;
import org.example.scope.PipelineContextHolder;
import org.example.step.PipelineContext;
import org.example.step.PipelineStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Orchestrator using the <b>Ephemeral Child Context Pattern</b>.
 * <p>
 * For every pipeline execution, this executor spins up a dedicated, short-lived
 * Spring ApplicationContext. This ensures perfect isolation of stateful components
 * and enables standard Spring features (AOP, Singletons) to behave dynamically
 * per request.
 * </p>
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
     * Executes the pipeline in an isolated child context.
     *
     * @param definition The pipeline blueprint.
     * @return The final result from the last step.
     * @throws DihCoreException If a known domain error occurs.
     * @throws PipelineConfigurationException If the context fails to start.
     */
    public Object executePipeline(PipelineDefinition definition,Object initialInput) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String executionId = UUID.randomUUID().toString();
        String pipelineName = definition.name();

        // 1. Initialize ThreadLocal Context (for MDC logs)
        PipelineContext pipelineContext = new PipelineContext(executionId, Instant.now().toEpochMilli(), pipelineName);
        PipelineContextHolder.initializeContext(pipelineContext);

        // 2. Create Ephemeral Child Context
        // WARN: Heavyweight operation. This is the main bottleneck of this architecture.
        try (var childContext = new AnnotationConfigApplicationContext()) {

            // 2.1 Context Hierarchy
            childContext.setParent(parentContext);
            childContext.setDisplayName("Child-Pipeline-" + executionId);

            // 2.2 Register Infrastructure Beans *specifically* for this child context
            // This ensures BPPs only affect beans in this isolation bubble.
            childContext.registerBean(RetryBeanPostProcessor.class);
            childContext.registerBean(DynamicContextBeanPostProcessor.class);

            // 2.3 Register Pipeline Steps via Registrar
            registrar.registerPipeline(definition, childContext);

            // 2.4 Ignite the Context (Dependency Injection, AOP Proxies created here)
            childContext.refresh();

            log.info("Pipeline '{}' started. ExecutionID: {}", pipelineName, executionId);

            // 3. Execution Loop
            Object currentData = initialInput;
            for (StepDefinition stepDef : definition.steps()) {
                String beanName = pipelineName + "_" + stepDef.id();

                // Retrieve the bean from the CHILD context
                Object stepBean = childContext.getBean(beanName);

                if (!(stepBean instanceof PipelineStep)) {
                    throw new IllegalStateException("Bean '" + beanName + "' is not a PipelineStep.");
                }

                @SuppressWarnings("unchecked")
                PipelineStep<Object, Object> step = (PipelineStep<Object, Object>) stepBean;

                // Execute
                currentData = step.execute(currentData, pipelineContext);
            }

            return currentData;

        } catch (DihCoreException e) {
            // Domain errors (Concurrency, RetryExhausted) should propagate up
            log.error("Pipeline execution failed [ID={}]: {}", executionId, e.getMessage());
            throw e;

        } catch (Exception e) {
            // Infrastructure errors (Context startup, DI failure)
            log.error("Infrastructure failure in pipeline [ID={}]", executionId, e);
            throw new PipelineConfigurationException("Fatal execution error: " + e.getMessage(), e);

        } finally {
            // 4. Cleanup
            PipelineContextHolder.cleanup();

            sample.stop(Timer.builder("dih.pipeline.execution")
                    .tag("pipeline.name", pipelineName)
                    .description("Total execution time including context startup")
                    .register(meterRegistry));

            log.debug("Pipeline context destroyed [ID={}]", executionId);
        }
    }

    public Object executePipeline(PipelineDefinition definition) {
        return executePipeline(definition, null);
    }
}