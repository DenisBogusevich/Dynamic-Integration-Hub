package org.example.exception;

// Ошибка, возникающая во время выполнения step.execute().
// PipelineExecutor должен оборачивать сюда пойманные Exception.
public class StepExecutionException extends DihCoreException {

    public StepExecutionException(String message, String stepId, Throwable cause) {
        // stepId используется как sourceName
        super("Step failed: " + message, stepId, cause);
    }

    public StepExecutionException(String message, String stepId) {
        super("Step failed: " + message, stepId);
    }
}