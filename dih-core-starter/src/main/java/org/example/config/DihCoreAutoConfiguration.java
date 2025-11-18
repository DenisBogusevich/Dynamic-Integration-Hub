package org.example.config;

import org.example.scope.PipelineScope;
import org.springframework.beans.factory.config.CustomScopeConfigurer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
}