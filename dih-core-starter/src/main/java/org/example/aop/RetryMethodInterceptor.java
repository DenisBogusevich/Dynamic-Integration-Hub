package org.example.aop;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.example.model.RetryPolicyDefinition;

/**
 * Interceptor that wraps the execution of a PipelineStep's execute() method
 * with a retry loop based on the configured policy.
 */
public class RetryMethodInterceptor implements MethodInterceptor {

    // Политика неизменяема и передается при создании прокси
    private final RetryPolicyDefinition retryPolicy;

    /**
     * Constructs the interceptor with the specific retry policy for this step's bean.
     * @param retryPolicy The fault tolerance configuration (maxAttempts, delay).
     */
    public RetryMethodInterceptor(RetryPolicyDefinition retryPolicy) {
        this.retryPolicy = retryPolicy;
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
                    System.err.println("RETRY EXHAUSTED: Max attempts (" + maxAttempts + ") reached. Throwing final exception.");
                    // КРИТИЧНО: Перебрасываем оригинальное исключение, чтобы его поймал PipelineExecutor.
                    throw e;
                }

                // 3. Продолжение: Логирование, задержка и переход к следующей итерации for-цикла.
                System.out.println("Attempt " + attempts + " failed. Retrying in " + delay + "ms.");
                Thread.sleep(delay);
            }
        }

        // Этот код должен быть недостижим благодаря for-циклу и throw e.
        throw new IllegalStateException("Critical error: Retry loop logic failed to terminate properly.");
    }
}