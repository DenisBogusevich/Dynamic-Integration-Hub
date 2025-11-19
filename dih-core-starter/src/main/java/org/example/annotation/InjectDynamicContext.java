package org.example.annotation;

import org.example.step.PipelineContext;
import java.lang.annotation.*;

/**
 * Marker annotation for fields that require runtime injection of {@link PipelineContext} metadata.
 *
 * <p>
 * Fields annotated with this will be populated by the {@code DynamicContextBeanPostProcessor}
 * during the bean initialization phase.
 * </p>
 *
 * <h2>Supported Field Types:</h2>
 * <ul>
 * <li>{@code String} - Injects the current <b>Execution ID</b>.</li>
 * <li>{@code Long} or {@code long} - Injects the pipeline <b>Start Time</b> (epoch millis).</li>
 * <li>{@link PipelineContext} - Injects the full context object.</li>
 * </ul>
 *
 * <h2> Architectural Warning (Scope Safety):</h2>
 * This annotation <b>MUST ONLY</b> be used on beans with  {@code @Scope("prototype")}.
 * <p>
 * If used on a <b>Singleton</b> bean, the context will be injected only once (during application startup
 * or first usage) and effectively "cached" forever. This will lead to <b>Context Leaks</b> where
 * subsequent pipeline executions incorrectly use the metadata from the first execution.
 * </p>
 *
 * @see org.example.bpp.DynamicContextBeanPostProcessor
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface InjectDynamicContext {
}