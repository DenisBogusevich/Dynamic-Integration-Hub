package org.example.scope;

import org.example.step.PipelineContext;
import org.slf4j.MDC;

/**
 * Static holder for the thread-bound {@link PipelineContext}.
 * <p>
 * <b>Responsibilities:</b>
 * <ul>
 * <li>Stores the current execution metadata (ID, start time) in {@link ThreadLocal}.</li>
 * <li>Synchronizes this metadata with SLF4J's {@link MDC} for structured logging.</li>
 * </ul>
 * <p>
 * <b>Note:</b> This class no longer manages Beans. Bean lifecycle is now managed
 * by the Ephemeral Child ApplicationContext.
 * </p>
 */
public class PipelineContextHolder {

    public static final String MDC_EXECUTION_ID = "execution.id";
    public static final String MDC_PIPELINE_NAME = "pipeline.name";

    // We only store Metadata now, not Beans.
    private static final ThreadLocal<PipelineContext> THREAD_CONTEXT = new ThreadLocal<>();

    /**
     * Binds the context to the current thread and updates MDC.
     *
     * @param context The metadata for the current pipeline run.
     */
    public static void initializeContext(PipelineContext context) {
        if (context == null) {
            cleanup();
            return;
        }

        THREAD_CONTEXT.set(context);

        // Populate Logging Context
        MDC.put(MDC_EXECUTION_ID, context.executionId());
        MDC.put(MDC_PIPELINE_NAME, context.pipelineName());
    }

    /**
     * Retrieves the current thread's pipeline context.
     *
     * @return The context, or {@code null} if not set (e.g., outside a pipeline).
     */
    public static PipelineContext getContext() {
        return THREAD_CONTEXT.get();
    }

    /**
     * Helper to retrieve just the Execution ID.
     *
     * @return Execution ID or null.
     */
    public static String getContextId() {
        PipelineContext ctx = THREAD_CONTEXT.get();
        return ctx != null ? ctx.executionId() : null;
    }

    /**
     * Clears the context from the current thread.
     * MUST be called in a `finally` block to prevent ThreadLocal leaks (memory leaks).
     */
    public static void cleanup() {
        // 1. Clear our custom context
        THREAD_CONTEXT.remove();

        // 2. Clear logging context to prevent confusing logs in reused threads
        MDC.remove(MDC_EXECUTION_ID);
        MDC.remove(MDC_PIPELINE_NAME);
    }
    // Deprecated methods removed for clarity (getCurrentBeans, getCurrentCallbacks)
}


