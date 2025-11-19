package org.example.annotation;

import org.springframework.stereotype.Component;

import java.lang.annotation.*;

/**
 * Marks a class as a discoverable Pipeline Step for the Dynamic Integration Hub.
 * <p>
 * Classes annotated with this are automatically registered in the {@code StepTypeRegistry}
 * and can be referenced in pipeline JSON definitions by their {@code value()}.
 * </p>
 *
 * Example:
 * <pre>
 * {@code
 * @DihStepComponent("JdbcSource")
 * public class JdbcSourceStep implements PipelineStep<Void, ResultSet> { ... }
 * }
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component // Meta-annotation: Spring will detect this as a bean
public @interface DihStepComponent {

    /**
     * The unique symbolic alias for this step type (e.g., "HttpSource").
     * This alias is used in the JSON configuration "type" field.
     */
    String value();
}