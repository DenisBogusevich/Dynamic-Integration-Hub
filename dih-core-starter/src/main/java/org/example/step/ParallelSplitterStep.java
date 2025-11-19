package org.example.step;

import org.example.annotation.DihStepComponent;
import org.example.exception.PipelineConcurrencyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.task.AsyncTaskExecutor;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

/**
 * Implements the <b>Scatter-Gather</b> Enterprise Integration Pattern.
 * <p>
 * This step acts as a composite node that:
 * <ol>
 * <li><b>Splits</b> the execution flow into multiple concurrent branches.</li>
 * <li><b>Executes</b> defined sub-steps using the configured {@link AsyncTaskExecutor}.</li>
 * <li><b>Aggregates</b> the results into a single {@code List}.</li>
 * </ol>
 *
 * <h2>Concurrency Model:</h2>
 * <ul>
 * <li><b>Fail-Fast:</b> If any single branch fails, the main thread catches the exception immediately
 * and aborts the entire pipeline execution via {@link PipelineConcurrencyException}.</li>
 * <li><b>Context Propagation:</b> Relies on {@code DihTaskDecorator} (configured in the Executor)
 * to propagate {@code ThreadLocal} context (MDC, Execution ID) to worker threads.</li>
 * </ul>
 *
 * @param <I> The input type passed to all parallel branches.
 * @param <O> The output type (always returns {@code List<Object>}).
 */
@DihStepComponent("ParallelSplitter")
public class ParallelSplitterStep<I, O> implements PipelineStep<I, O>, ApplicationContextAware {

    private static final Logger log = LoggerFactory.getLogger(ParallelSplitterStep.class);

    private ApplicationContext springContext;

    @Autowired
    private AsyncTaskExecutor dihTaskExecutor;

    /**
     * The identifiers of the steps to run in parallel.
     *
     * @deprecated <b>Architectural Warning:</b> Relying on String IDs forces a runtime lookup (Service Locator pattern).
     * <br><b>Improvement:</b> Refactor {@code PipelineRegistrar} to wire a {@code List<PipelineStep>} directly into this bean
     * during the definition phase. This would provide compile-time safety and eager validation.
     */
    @Deprecated
    private List<String> subStepIds;

    /**
     * Injected via setter from the {@code StepDefinition} properties.
     */
    public void setSubStepIds(List<String> subStepIds) {
        this.subStepIds = subStepIds;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.springContext = applicationContext;
    }

    /**
     * Executes the parallel orchestration.
     *
     * @param input           The payload broadcast to all branches.
     * @param pipelineContext The current execution metadata.
     * @return A {@link List} of results from all branches.
     * @throws PipelineConcurrencyException if any branch fails.
     */
    @Override
    @SuppressWarnings("unchecked")
    public O execute(I input, PipelineContext pipelineContext) {

        if (subStepIds == null || subStepIds.isEmpty()) {
            log.warn("ParallelSplitter defined without sub-steps. Returning null.");
            return null;
        }

        String pipelineName = pipelineContext.pipelineName();

        // 1. Scatter: Submit tasks to the thread pool
        List<CompletableFuture<Object>> futures = subStepIds.stream()
                .map(stepId -> {
                    // ARCHITECTURAL NOTE: Naming convention coupling (PipelineName + "_" + StepId)
                    String beanName = pipelineName + "_" + stepId;

                    return CompletableFuture.supplyAsync(() -> executeSubStep(beanName, input, pipelineContext), dihTaskExecutor);
                })
                .toList();

        // 2. Monitor: Create a barrier waiting for all tasks
        CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

        try {
            // Block until all are done. If any future completes exceptionally, join() throws CompletionException.
            allOf.join();

        } catch (CompletionException e) {
            // --- FAIL FAST LOGIC ---
            Throwable realCause = e.getCause();
            log.error("Parallel execution failed in pipeline '{}'. Aborting.", pipelineName, realCause);

            throw new PipelineConcurrencyException(
                    "One or more parallel steps failed. See cause for details.",
                    pipelineName,
                    realCause
            );
        }

        // 3. Gather: Collect results
        List<Object> results = futures.stream()
                .map(CompletableFuture::join) // Safe to join here as we passed the barrier
                .collect(Collectors.toList());

        log.debug("ParallelSplitter aggregated {} results.", results.size());

        return (O) results;
    }

    /**
     * Helper to locate and execute a single step bean.
     *
     * @deprecated <b>Performance & Design Issue:</b> This method performs a Bean Lookup inside the hot execution path.
     */
    @Deprecated
    private Object executeSubStep(String beanName, I input, PipelineContext context) {
        // 1. Service Locator Call (Pulling dependencies)
        Object bean = springContext.getBean(beanName);

        if (!(bean instanceof PipelineStep)) {
            throw new IllegalStateException("Bean '" + beanName + "' must implement PipelineStep.");
        }

        @SuppressWarnings("unchecked")
        PipelineStep<Object, Object> step = (PipelineStep<Object, Object>) bean;

        try {
            // 2. Execution
            return step.execute(input, context);
        } catch (Exception e) {
            // Wrap checked exceptions to runtime exceptions for CompletableFuture compatibility
            throw new RuntimeException("Step execution failed: " + beanName, e);
        }
    }
}