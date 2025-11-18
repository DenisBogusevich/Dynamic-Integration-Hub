package org.example.bpp;

import org.example.annotation.InjectDynamicContext;
import org.example.scope.PipelineContextHolder;
import org.example.step.PipelineContext;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;

@Component
public class DynamicContextBeanPostProcessor implements BeanPostProcessor {

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        // Сканируем все поля бина
        ReflectionUtils.doWithFields(bean.getClass(), field -> {
            // Ищем нашу аннотацию
            if (field.isAnnotationPresent(InjectDynamicContext.class)) {
                injectContextData(bean, field);
            }
        });
        return bean;
    }

    private void injectContextData(Object bean, Field field) {
        PipelineContext currentContext = PipelineContextHolder.getContext();

        if (currentContext == null) {
            // Если бин создается вне пайплайна (например, при старте контекста),
            // мы не можем ничего внедрить. Пропускаем или логируем warning.
            return;
        }

        ReflectionUtils.makeAccessible(field);

        Object valueToInject = null;
        Class<?> fieldType = field.getType();

        // Определяем, что именно внедрять, основываясь на типе поля
        if (fieldType.equals(String.class)) {
            // По умолчанию внедряем ID, если поле String
            valueToInject = currentContext.executionId();
        } else if (fieldType.equals(Long.class) || fieldType.equals(long.class)) {
            // Если long - внедряем startTime
            valueToInject = currentContext.startTime();
        } else if (fieldType.equals(PipelineContext.class)) {
            // Если запрошен весь объект контекста
            valueToInject = currentContext;
        }

        if (valueToInject != null) {
            ReflectionUtils.setField(field, bean, valueToInject);
        }
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }
}