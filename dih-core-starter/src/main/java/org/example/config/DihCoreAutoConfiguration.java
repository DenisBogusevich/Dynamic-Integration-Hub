package org.example.config;

import org.example.concurrency.DihTaskDecorator;
import org.example.scope.PipelineScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties; // <--- Важно
import org.springframework.beans.factory.config.CustomScopeConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableConfigurationProperties(DihProperties.class) // <--- Включаем наши проперти
public class DihCoreAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(DihCoreAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public PipelineScope pipelineScope() {
        return new PipelineScope();
    }

    @Bean
    public CustomScopeConfigurer customScopeConfigurer(PipelineScope pipelineScope) {
        CustomScopeConfigurer configurer = new CustomScopeConfigurer();
        configurer.addScope("pipeline", pipelineScope);
        return configurer;
    }

    @Bean
    @ConditionalOnMissingBean(name = "dihTaskExecutor")
    public ThreadPoolTaskExecutor dihTaskExecutor(DihProperties properties) { // <--- Инжектируем настройки
        log.info("Initializing DIH ThreadPool with Core={}, Max={}, Queue={}",
                properties.getCorePoolSize(), properties.getMaxPoolSize(), properties.getQueueCapacity());

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Берем значения из properties, а не хардкод
        executor.setCorePoolSize(properties.getCorePoolSize());
        executor.setMaxPoolSize(properties.getMaxPoolSize());
        executor.setQueueCapacity(properties.getQueueCapacity());
        executor.setThreadNamePrefix(properties.getThreadNamePrefix());

        executor.setTaskDecorator(new DihTaskDecorator());
        executor.initialize();
        return executor;
    }
}