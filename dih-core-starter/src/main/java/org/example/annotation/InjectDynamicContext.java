package org.example.annotation;

import java.lang.annotation.*;

/**
 * Помечает поля, в которые нужно внедрить данные из текущего PipelineContext
 * (executionId, startTime) во время создания бина.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface InjectDynamicContext {
}