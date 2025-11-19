package org.example.aop;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.example.exception.RetryExhaustedException;
import org.example.model.RetryPolicyDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Interceptor that wraps the execution of a PipelineStep's execute() method
 * with a retry loop based on the configured policy.
 */
public class RetryMethodInterceptor implements MethodInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RetryMethodInterceptor.class);

    // Политика неизменяема и передается при создании прокси
    private final RetryPolicyDefinition retryPolicy;
    private final Counter retryCounter;
    private final String stepId;

    /**
     * Constructs the interceptor with the specific retry policy for this step's bean.
     * @param retryPolicy The fault tolerance configuration (maxAttempts, delay).
     * @param meterRegistry The Micrometer registry instance.
     * @param beanName The unique bean name (e.g., PipelineName_StepId) for tagging.
     */
    public RetryMethodInterceptor(RetryPolicyDefinition retryPolicy, MeterRegistry meterRegistry, String beanName) {
        this.retryPolicy = retryPolicy;

        String[] parts = beanName.split("_", 2);
        String pipelineName = parts.length > 0 ? parts[0] : "unknown";
        this.stepId = parts.length > 1 ? parts[1] : beanName;

        this.retryCounter = Counter.builder("dih.step.retries")
                .tag("pipeline.name", pipelineName)
                .tag("step.id", stepId)
                .description("Counts failed attempts that triggered a retry for a step.")
                .register(meterRegistry);
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        int maxAttempts = retryPolicy.maxAttempts();
        long delay = retryPolicy.delay();

        // Loop runs from 1 to maxAttempts. This controls the total number of calls
        // to invocation.proceed().
        for (int attempts = 1; attempts <= maxAttempts; attempts++) {
            try {
                // 1. Успешный вызов: Немедленный выход из метода и цикла.
                return invocation.proceed();

            } catch (Exception e) {

                // 2. Проверка лимита: Если это последняя разрешенная попытка
                if (attempts == maxAttempts) {
                    log.error("RETRY EXHAUSTED: Step '{}' failed after {} attempts.", stepId, maxAttempts, e);
                    throw new RetryExhaustedException(
                            // stepId нам нужен, мы его выделили в конструкторе интерцептора
                            // (он вложен в поле beanName или можно добавить отдельное поле)
                            // Предположим, что stepId доступен в этом классе.
                            stepId,
                            maxAttempts,
                            e // Передаем оригинальное исключение как причину
                    );
                }

                // 3. Продолжение: Логирование, задержка и переход к следующей итерации for-цикла.
                log.warn("Attempt {}/{} failed for step '{}'. Retrying in {}ms. Error: {}",
                        attempts, maxAttempts, stepId, delay, e.getMessage());
                this.retryCounter.increment();
                Thread.sleep(delay);
            }
        }

        // Этот код должен быть недостижим благодаря for-циклу и throw e.
        throw new IllegalStateException("Critical error: Retry loop logic failed to terminate properly.");
    }
}