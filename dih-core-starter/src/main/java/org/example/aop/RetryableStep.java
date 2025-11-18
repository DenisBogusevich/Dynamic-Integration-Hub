package org.example.aop;

import java.lang.annotation.*;

/**
 * Marker annotation applied to PipelineStep classes that should be wrapped
 * in a retry proxy by the custom BeanPostProcessor.
 * The actual retry policy parameters (maxAttempts, delay) are read from the
 * StepDefinition DTO at runtime by the MethodInterceptor.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RetryableStep {
}