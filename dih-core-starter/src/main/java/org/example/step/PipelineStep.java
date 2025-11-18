package org.example.step;

/**
 * Defines the contract for all processing components within the Dynamic Integration Hub (DIH).
 * All Sources, Processors, and Sinks must implement this interface.
 *
 * @param <I> The type of the input data received from the previous step.
 * For a Source, this is typically Void or an initial context object.
 * @param <O> The type of the output data passed to the next step.
 * For a Sink, this is typically Void or a completion status.
 */
public interface PipelineStep<I,O> {
    /**
     * Executes the processing logic of the step.
     *
     * @param input The data payload received from the preceding step.
     * @param context The runtime context for the current pipeline execution (e.g., ID, metrics).
     * @return The data payload to be passed to the succeeding step.
     * @throws Exception if the step execution fails (e.g., connection issues, data errors).
     */
    O execute(I input,PipelineContext context) throws Exception ;
}
