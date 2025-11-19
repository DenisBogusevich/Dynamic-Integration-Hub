package org.example.exception;

// Ошибка, связанная с параллельным исполнением, в основном в ParallelSplitterStep.
public class PipelineConcurrencyException extends DihCoreException {

    public PipelineConcurrencyException(String message, String pipelineName, Throwable cause) {
        // pipelineName используется как sourceName
        super("Concurrency error in pipeline: " + message, pipelineName, cause);
    }

    public PipelineConcurrencyException(String message, String pipelineName) {
        super("Concurrency error in pipeline: " + message, pipelineName);
    }
}