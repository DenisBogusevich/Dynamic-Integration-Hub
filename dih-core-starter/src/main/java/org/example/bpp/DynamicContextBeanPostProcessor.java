package org.example.bpp;

import org.example.annotation.InjectDynamicContext;
import org.example.scope.PipelineContextHolder;
import org.example.step.PipelineContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;

/**
 * <b>Infrastructure Component:</b> Handles the runtime injection of execution context metadata.
 * <p>
 * This BeanPostProcessor scans beans for fields annotated with {@link InjectDynamicContext}
 * and populates them with data from the current {@link PipelineContextHolder}.
 * </p>
 *
 * <h2>Architectural Warning (Critical Risk):</h2>
 * This processor is <b>unsafe for Singleton beans</b>.
 * <ul>
 * <li>Spring beans are Singletons by default.</li>
 * <li>{@code postProcessBeforeInitialization} runs only once per bean instance.</li>
 * <li>If this runs on a Singleton, the context ID is captured <b>once</b> and cached forever.</li>
 * <li>Subsequent pipeline runs will see the stale/wrong execution ID.</li>
 * </ul>
 *
 * <p>
 * <b>Recommendation:</b> Use this ONLY with beans annotated with {@code @PipelineScope}
 * or {@code @Scope("prototype")}.
 * </p>
 */
@Component
public class DynamicContextBeanPostProcessor implements BeanPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(DynamicContextBeanPostProcessor.class);

    /**
     * Scans the bean for annotated fields and injects context data if available.
     *
     * @param bean     The new bean instance.
     * @param beanName The name of the bean.
     * @return The bean instance (potentially modified).
     * @throws BeansException in case of fatal errors.
     */
    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        // Scan all fields in the class hierarchy
        ReflectionUtils.doWithFields(bean.getClass(), field -> {
            if (field.isAnnotationPresent(InjectDynamicContext.class)) {
                try {
                    injectContextData(bean, field, beanName);
                } catch (Exception e) {
                    log.error("Failed to inject dynamic context into bean '{}', field '{}'", beanName, field.getName(), e);
                    // We catch exception to not block application startup, but this is a critical configuration error.
                }
            }
        });
        return bean;
    }

    /**
     * Performs the reflection-based injection.
     *
     * @deprecated <b>Design Flaw:</b> Field Injection hides dependencies and makes testing difficult.
     * It also bypasses the constructor contract.
     * <br><b>Fix:</b> Prefer passing {@link PipelineContext} explicitly as a method argument in {@code execute()},
     * or ensure the receiving bean is strictly Scope-managed.
     *
     * @param bean     The target object.
     * @param field    The target field.
     * @param beanName For logging purposes.
     */
    @Deprecated
    private void injectContextData(Object bean, Field field, String beanName) {
        PipelineContext currentContext = PipelineContextHolder.getContext();

        if (currentContext == null) {
            // Common scenario during eager initialization of Singletons at startup.
            log.debug("No active PipelineContext found for bean '{}'. Skipping injection for field '{}'.",
                    beanName, field.getName());
            return;
        }

        ReflectionUtils.makeAccessible(field);

        Object valueToInject = resolveValue(field.getType(), currentContext);

        if (valueToInject != null) {
            ReflectionUtils.setField(field, bean, valueToInject);
            log.trace("Injected '{}' into bean '{}' field '{}'", valueToInject, beanName, field.getName());
        } else {
            log.warn("Unsupported field type '{}' for @InjectDynamicContext in bean '{}'",
                    field.getType().getName(), beanName);
        }
    }

    /**
     * Resolves the value to inject based on the field type.
     */
    private Object resolveValue(Class<?> fieldType, PipelineContext context) {
        if (fieldType.equals(String.class)) {
            return context.executionId();
        } else if (fieldType.equals(Long.class) || fieldType.equals(long.class)) {
            return context.startTime();
        } else if (fieldType.equals(PipelineContext.class)) {
            return context;
        }
        return null;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        // No operation needed after initialization
        return bean;
    }
}