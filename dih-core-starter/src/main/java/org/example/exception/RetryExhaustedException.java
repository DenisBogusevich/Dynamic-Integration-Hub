package org.example.exception;

// Ошибка, которую будет бросать RetryMethodInterceptor, когда попытки исчерпаны.
public class RetryExhaustedException extends StepExecutionException {

    public RetryExhaustedException(String stepId, int maxAttempts, Throwable lastCause) {
        super("Max retry attempts (" + maxAttempts + ") exhausted for step.", stepId, lastCause);
    }
}