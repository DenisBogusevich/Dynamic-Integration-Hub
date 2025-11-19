package org.example.aop;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.example.exception.RetryExhaustedException;
import org.example.model.RetryPolicyDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * AOP Advice that implements the Retry Logic.
 * <p>
 * Wraps the execution of the {@code execute} method. If an exception occurs,
 * it pauses the thread and retries until the maximum attempts are reached.
 * </p>
 */
public class RetryMethodInterceptor implements MethodInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RetryMethodInterceptor.class);

    private final RetryPolicyDefinition retryPolicy;
    private final Counter retryCounter;
    private final String stepId;

    public RetryMethodInterceptor(RetryPolicyDefinition retryPolicy, MeterRegistry meterRegistry, String beanName) {
        this.retryPolicy = retryPolicy;

        // Extract clean ID from "PipelineName_StepId"
        String[] parts = beanName.split("_", 2);
        String pipelineName = parts.length > 0 ? parts[0] : "unknown";
        this.stepId = parts.length > 1 ? parts[1] : beanName;

        this.retryCounter = Counter.builder("dih.step.retries")
                .tag("pipeline.name", pipelineName)
                .tag("step.id", stepId)
                .description("Counts failed attempts that triggered a retry.")
                .register(meterRegistry);
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        // 1. Filtering: Only intercept the 'execute' method
        // This prevents retrying hashCode(), toString(), or setters.
        Method method = invocation.getMethod();
        if (!"execute".equals(method.getName())) {
            return invocation.proceed();
        }

        int maxAttempts = retryPolicy.maxAttempts();
        long delay = retryPolicy.delay();

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return invocation.proceed();
            } catch (Exception e) {
                if (attempt == maxAttempts) {
                    log.error("RETRY EXHAUSTED: Step '{}' failed after {} attempts.", stepId, maxAttempts);
                    throw new RetryExhaustedException(stepId, maxAttempts, e);
                }

                log.warn("Attempt {}/{} failed for step '{}'. Retrying in {}ms. Error: {}",
                        attempt, maxAttempts, stepId, delay, e.getMessage());

                this.retryCounter.increment();

                // ARCHITECTURAL WARNING: Blocking I/O
                performWait(delay);
            }
        }
        throw new IllegalStateException("Unreachable code in RetryMethodInterceptor");
    }

    /**
     * Pauses the current thread.
     *
     * @deprecated <b>Performance Bottleneck:</b> Calling {@code Thread.sleep} blocks the actual
     * worker thread from the pool. In high-concurrency scenarios (like your {@code ParallelSplitterStep}),
     * this leads to Thread Starvation.
     * <br><b>Fix:</b> Switch to a non-blocking retry mechanism (e.g., Spring Retry with BackOffPolicy
     * or Reactor's {@code .retryWhen()}) if moving to a reactive stack. For synchronous flows,
     * this is acceptable ONLY if pool size is sufficient.
     */
    @Deprecated
    private void performWait(long delay) {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Retry interrupted", e);
        }
    }
}