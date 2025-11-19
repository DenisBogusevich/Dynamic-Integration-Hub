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
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executors;

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

    @Bean(name = "dihTaskExecutor")
    @ConditionalOnMissingBean(name = "dihTaskExecutor")
    public AsyncTaskExecutor dihTaskExecutor(DihProperties properties) {
        log.info("Initializing DIH Executor with Virtual Threads (Java 21+)");

        // Создаем фабрику, которая именует потоки (полезно для дебага)
        var factory = Thread.ofVirtual()
                .name(properties.getThreadNamePrefix(), 0)
                .factory();

        // Создаем ExecutorService на виртуальных потоках
        var virtualExecutor = Executors.newThreadPerTaskExecutor(factory);

        // Оборачиваем в Spring-совместимый адаптер
        TaskExecutorAdapter adapter = new TaskExecutorAdapter(virtualExecutor);

        // Подключаем наш TaskDecorator для проброса контекста
        adapter.setTaskDecorator(new DihTaskDecorator());

        return adapter;
    }
}