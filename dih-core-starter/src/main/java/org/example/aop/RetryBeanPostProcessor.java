package org.example.aop;

import io.micrometer.core.instrument.MeterRegistry;
import org.example.model.RetryPolicyDefinition;
import org.example.step.PipelineStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.stereotype.Component;

/**
 * Infrastructure component that applies Dynamic AOP Proxies to Pipeline Steps.
 * <p>
 * This processor inspects the {@link BeanDefinition} of every {@link PipelineStep}.
 * If a {@link RetryPolicyDefinition} attribute is found (injected by the Registrar),
 * it wraps the bean in a Spring AOP Proxy with a {@link RetryMethodInterceptor}.
 * </p>
 */
@Component
public class RetryBeanPostProcessor implements BeanPostProcessor, BeanFactoryAware {

    private static final Logger log = LoggerFactory.getLogger(RetryBeanPostProcessor.class);

    private ConfigurableListableBeanFactory beanFactory;
    private final MeterRegistry meterRegistry;

    public RetryBeanPostProcessor(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        if (beanFactory instanceof ConfigurableListableBeanFactory clbf) {
            this.beanFactory = clbf;
        }
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        // 1. Filter: We only care about PipelineSteps
        if (!(bean instanceof PipelineStep)) {
            return bean;
        }

        // 2. Metadata Lookup: Check if this specific step definition has a retry policy
        RetryPolicyDefinition policy = getRetryPolicyFromDefinition(beanName);

        if (policy == null || policy.maxAttempts() <= 1) {
            return bean;
        }

        // 3. Proxy Creation
        ProxyFactory proxyFactory = new ProxyFactory(bean);
        proxyFactory.setProxyTargetClass(true); // Enforce CGLIB (Class-based proxy)
        proxyFactory.addAdvice(new RetryMethodInterceptor(policy, meterRegistry, beanName));

        // Use Logger instead of System.out
        log.info("Applied Retry AOP Proxy to step '{}'. Policy: [Max={}, Delay={}ms]",
                beanName, policy.maxAttempts(), policy.delay());

        return proxyFactory.getProxy();
    }

    /**
     * Safely retrieves the retry policy from the bean definition attributes.
     */
    private RetryPolicyDefinition getRetryPolicyFromDefinition(String beanName) {
        if (beanFactory == null || !beanFactory.containsBeanDefinition(beanName)) {
            return null;
        }

        try {
            BeanDefinition definition = beanFactory.getBeanDefinition(beanName);
            Object policyAttribute = definition.getAttribute("retryPolicy");

            if (policyAttribute instanceof RetryPolicyDefinition) {
                return (RetryPolicyDefinition) policyAttribute;
            }
        } catch (Exception e) {
            // Use Logger instead of System.err
            log.warn("Failed to retrieve BeanDefinition for '{}' during AOP processing. Skipping retry configuration.", beanName);
        }

        return null;
    }
}