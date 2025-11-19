package org.example.config;

import org.example.registry.StepTypeRegistry;
import org.example.scope.PipelineScope;
import org.example.service.PipelineRegistrar;
import org.springframework.beans.factory.config.CustomScopeConfigurer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.GenericApplicationContext;

@TestConfiguration
public class DihCoreTestConfig {

    //@Bean
    //public PipelineScope pipelineScope() {
     //   return new PipelineScope();
    //}

    // Регистрируем наш Scope под именем "pipeline"
    //@Bean
    //public CustomScopeConfigurer customScopeConfigurer(PipelineScope pipelineScope) {
      //  CustomScopeConfigurer configurer = new CustomScopeConfigurer();
        //configurer.addScope("pipeline", pipelineScope);
        //return configurer;
   // }

    // Предоставляем реальные зависимости для PipelineRegistrar

}