package org.example.aop;

import io.micrometer.core.instrument.MeterRegistry;
import org.example.model.RetryPolicyDefinition;
import org.example.step.PipelineStep; // Используем наш контракт

import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.stereotype.Component;

/**
 * Custom BeanPostProcessor to apply a retry-enabling AOP proxy to PipelineStep
 * beans that have a RetryPolicyDefinition configured in their BeanDefinition metadata.
 */
@Component
public class RetryBeanPostProcessor implements BeanPostProcessor, BeanFactoryAware {

    private ConfigurableListableBeanFactory beanFactory;
    private final MeterRegistry meterRegistry;

    @Autowired
    public RetryBeanPostProcessor(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Injects the BeanFactory, allowing access to low-level BeanDefinitions and attributes.
     */
    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        // Сохраняем расширенный интерфейс фабрики для доступа к BeanDefinition
        if (beanFactory instanceof ConfigurableListableBeanFactory) {
            this.beanFactory = (ConfigurableListableBeanFactory) beanFactory;
        }
    }

    /**
     * Intercepts bean creation after initialization to apply AOP proxying.
     * This method is called once for every bean instance after it has been created and populated.
     */
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {

        // 1. Фильтруем бины, которые нас не интересуют
        if (!(bean instanceof PipelineStep)) {
            return bean;
        }

        // 2. Получаем политику из метаданных BeanDefinition
        RetryPolicyDefinition policy = getRetryPolicyFromDefinition(beanName);

        // 3. Если политика не настроена или повторы не нужны, пропускаем
        if (policy == null || policy.maxAttempts() <= 1) {
            // Также проверяем @RetryableStep, если вы решите использовать его как триггер
            // в дополнение к политике. Для чистоты: policy!=null — основной триггер.
            return bean;
        }

        // 4. Создание AOP Прокси
        ProxyFactory proxyFactory = new ProxyFactory(bean);

        // Форсируем использование CGLIB для проксирования класса,
        // а не только интерфейсов. Это безопаснее для бинов в кастомном скоупе.
        proxyFactory.setProxyTargetClass(true);

        // 5. Добавление нашего интерцептора с инжектированной политикой
        // Мы передаем DTO RetryPolicyDefinition в конструктор интерцептора
        proxyFactory.addAdvice(new RetryMethodInterceptor(policy, meterRegistry, beanName));
        System.out.println("Applied Retry Proxy to bean: " + beanName +
                " (Max Attempts: " + policy.maxAttempts() + ", Delay: " + policy.delay() + "ms)");

        // 6. Возвращаем прокси
        return proxyFactory.getProxy();
    }

    /**
     * Helper method to retrieve the custom RetryPolicyDefinition from the BeanDefinition metadata.
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
            // В случае ошибки получения BeanDefinition (например, это скоуп-прокси)
            System.err.println("Could not retrieve BeanDefinition for AOP processing: " + beanName);
        }

        return null;
    }
}