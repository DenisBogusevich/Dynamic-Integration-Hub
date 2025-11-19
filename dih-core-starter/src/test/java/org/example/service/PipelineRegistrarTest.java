package org.example.service;

import org.example.config.DihCoreTestConfig;
import org.example.exception.StepTypeNotFoundException;
import org.example.model.StepDefinition;
import org.example.registry.StepTypeRegistry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.CustomScopeConfigurer;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.context.support.SimpleThreadScope;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Import(DihCoreTestConfig.class)
class PipelineRegistrarTest {

    @Autowired
    private PipelineRegistrar pipelineRegistrar;

    @Autowired
    private StepTypeRegistry stepTypeRegistry;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private BeanDefinitionRegistry beanDefinitionRegistry;

    // 1. Тестовый класс-пустышка (Dummy Step)
    // Он имитирует реальный компонент пайплайна
    static class TestStep {
        private String testField;
        private int count;

        // Важно: Сеттеры обязательны для property injection!
        public void setTestField(String testField) {
            this.testField = testField;
        }
        public void setCount(int count) {
            this.count = count;
        }

        public String getTestField() { return testField; }
        public int getCount() { return count; }
    }

    @Test
    @DisplayName("Should dynamically register a bean with properties and correct name")
    void shouldRegisterBeanDynamically() {
        String pipelineName = "testPipeline";
        String stepId = "step1";
        String stepType = "TestStepAlias";

        stepTypeRegistry.register(stepType, TestStep.class);

        Map<String, Object> properties = new HashMap<>();
        properties.put("testField", "Hello World");
        properties.put("count", 42);

        StepDefinition stepDef = new StepDefinition(
                stepId,
                stepType,
                properties,
                null,
                null
        );

        pipelineRegistrar.registerStep(pipelineName, stepDef,beanDefinitionRegistry);

        String expectedBeanName = pipelineName + "_" + stepId;

        boolean beanExists = applicationContext.containsBean(expectedBeanName);
        assertTrue(beanExists, "Bean should be registered in the context");

        Object bean = applicationContext.getBean(expectedBeanName);
        assertInstanceOf(TestStep.class, bean);

        TestStep typedBean = (TestStep) bean;
        assertEquals("Hello World", typedBean.getTestField());
        assertEquals(42, typedBean.getCount());

        System.out.println("Test passed! Bean created: " + typedBean);
    }
}