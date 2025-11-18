package org.example.config;

import org.example.concurrency.DihTaskDecorator;
import org.example.scope.PipelineScope;
import org.springframework.beans.factory.config.CustomScopeConfigurer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class DihCoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public PipelineScope pipelineScope() {
        return new PipelineScope();
    }

    @Bean
    public CustomScopeConfigurer customScopeConfigurer(PipelineScope pipelineScope) {
        CustomScopeConfigurer configurer = new CustomScopeConfigurer();
        // Связываем строковое имя "pipeline" с нашей реализацией
        configurer.addScope("pipeline", pipelineScope);
        return configurer;
    }

    @Bean
    @ConditionalOnMissingBean(name = "dihTaskExecutor")
    public ThreadPoolTaskExecutor dihTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // Настройка пула (эти параметры можно вынести в application.yml)
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(25);
        executor.setThreadNamePrefix("dih-parallel-");

        // КРИТИЧЕСКИ ВАЖНО: Установка декоратора контекста
        executor.setTaskDecorator(new DihTaskDecorator());

        executor.initialize();
        return executor;
    }
}