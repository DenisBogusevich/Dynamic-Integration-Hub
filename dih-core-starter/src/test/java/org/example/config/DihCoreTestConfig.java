package org.example.config;

import org.springframework.boot.test.context.TestConfiguration;

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