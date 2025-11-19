package org.example.bpp;

import org.example.annotation.DihStepComponent;
import org.example.registry.StepTypeRegistry;
import org.example.step.PipelineStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;
import org.springframework.aop.framework.AopProxyUtils;

@Component
public class StepRegistryBeanPostProcessor implements BeanPostProcessor, ApplicationContextAware {

    private static final Logger log = LoggerFactory.getLogger(StepRegistryBeanPostProcessor.class);

    private final StepTypeRegistry registry;
    private ApplicationContext applicationContext;

    public StepRegistryBeanPostProcessor(StepTypeRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class<?> targetClass = AopProxyUtils.ultimateTargetClass(bean);

        if (targetClass.isAnnotationPresent(DihStepComponent.class)) {

            if (!PipelineStep.class.isAssignableFrom(targetClass)) {
                log.error("Bean '{}' is annotated with @DihStepComponent but does not implement PipelineStep. Ignoring.", beanName);
                return bean;
            }

            DihStepComponent annotation = targetClass.getAnnotation(DihStepComponent.class);
            String stepType = annotation.value();

            @SuppressWarnings("unchecked")
            Class<? extends PipelineStep<?, ?>> stepClass = (Class<? extends PipelineStep<?, ?>>) targetClass;

            registry.registerStep(stepType, stepClass);
        }
        return bean;
    }
}