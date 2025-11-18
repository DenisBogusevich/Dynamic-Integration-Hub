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
        // Проверяем, что политика не null.
        // В идеале BeanPostProcessor не должен создавать прокси, если policy==null,
        // но эта проверка добавляет устойчивости.
        if (retryPolicy == null || retryPolicy.maxAttempts() <= 1) {
            return invocation.proceed();
        }

        int maxAttempts = retryPolicy.maxAttempts();
        long delay = retryPolicy.delay();
        int attempts = 0;

        while (true) { // Бесконечный цикл, выход из которого контролируется return/throw
            attempts++;
            try {
                // 1. Попытка выполнения оригинального метода (execute())
                return invocation.proceed();
            } catch (Exception e) {

                // 2. Проверка, исчерпаны ли попытки
                if (attempts >= maxAttempts) {
                    // Последняя попытка провалена, выбрасываем оригинальное исключение
                    System.err.println("Execution failed after " + attempts +
                            " attempts for method " + invocation.getMethod().getName());
                    throw e;
                }

                // 3. Логирование и backoff
                System.out.println("Attempt " + attempts + " failed. Retrying in " + delay + "ms. Error: " + e.getMessage());
                // В продакшене тут можно добавить Jitter к задержке
                Thread.sleep(delay);
            }
        }
    }
}