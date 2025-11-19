package org.example.integration;

import org.example.config.DihCoreTestConfig;
import org.example.model.PipelineDefinition;
import org.example.model.StepDefinition;
import org.example.registry.StepTypeRegistry;
import org.example.scope.PipelineContextHolder;
import org.example.service.PipelineExecutor;
import org.example.service.PipelineRegistrar;
import org.example.step.PipelineStep; // Предполагается, что DIH-101 завершен
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.GenericApplicationContext;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = DihCoreTestConfig.class)
@Import(TestComponents.class) // Для доступа к тестовым классам
class PipelineExecutionTest {

    private static final String PIPELINE_NAME = "TestOrderFlow";

    @Autowired
    private PipelineRegistrar registrar;

    @Autowired
    private StepTypeRegistry registry;

    @Autowired
    private PipelineExecutor executor; // Предполагается, что DIH-102 завершен

    @Autowired
    private GenericApplicationContext gcontext;

    @Autowired
    private BeanDefinitionRegistry beanDefinitionRegistry;

    @BeforeEach
    void setup() {
        // 1. Регистрация тестовых типов в реестре
        registry.register("Source", TestComponents.StringTestSource.class);
        registry.register("Processor", TestComponents.StringLengthProcessor.class);
        registry.register("Sink", TestComponents.DataStorageSink.class);

        // 2. Сброс статического поля для каждого теста
        TestComponents.DataStorageSink.finalResult = null;
    }

    @AfterEach
    void cleanup() {
        // Удаляем определения бинов после теста, чтобы не засорять контекст
        String[] stepIds = {"source-step", "processor-step", "sink-step"};
        for (String stepId : stepIds) {
            String beanName = PIPELINE_NAME + "_" + stepId;
            if (gcontext.containsBeanDefinition(beanName)) {
                gcontext.removeBeanDefinition(beanName);
            }
        }
    }

    @Test
    @DisplayName("E2E: Should register, execute pipeline, and correctly pass data through steps")
    void shouldExecutePipelineSuccessfully() {
        // 1. Сборка PipelineDefinition
        StepDefinition sourceStep = new StepDefinition(
                "source-step", "Source", Map.of("fixedValue", "Hello World DIH!"), null, null);

        StepDefinition processorStep = new StepDefinition(
                "processor-step", "Processor", Map.of(), null, null);

        StepDefinition sinkStep = new StepDefinition(
                "sink-step", "Sink", Map.of(), null, null);

        PipelineDefinition definition = new PipelineDefinition(
                PIPELINE_NAME, "pipeline", "1.0", List.of(sourceStep, processorStep, sinkStep)
        );

        // 2. Регистрация пайплайна
        registrar.registerPipeline(definition,beanDefinitionRegistry);

        // Проверка: все бины должны быть зарегистрированы
        assertTrue(gcontext.containsBeanDefinition("TestOrderFlow_source-step"));
        assertTrue(gcontext.containsBeanDefinition("TestOrderFlow_processor-step"));

        // 3. Выполнение пайплайна
        Object finalOutput = executor.executePipeline(definition);

        // 4. Проверки (Assertions)

        // Ассерт 1: Результат Sink должен быть null (он ничего не возвращает)
        assertNull(finalOutput, "Final output of the execution should be null (consumed by Sink)");

        // Ассерт 2: Проверяем состояние Sink
        // Source output (String): "Hello World DIH! - ExecID:..."
        // Processor output (Integer): length of the string (e.g., 29 + length of UUID part)

        Integer storedLength = TestComponents.DataStorageSink.finalResult;

        assertNotNull(storedLength, "Sink should have stored the final calculated length.");

        // Проверка корректности данных: длина строки "Hello World DIH! - ExecID:" (25) + длина UUID (36)
        // UUID (36 символов) + " - ExecID:" (10 символов) + "Hello World DIH!" (16 символов) = 62
        // Но фактическая длина строки будет 'Hello World DIH! - ExecID: [UUID]'
        // Длина "Hello World DIH! - ExecID:" = 25
        // Длина UUID = 36
        // ИТОГО: 25 + 36 = 61 символ.

        String expectedPrefix = (String) sourceStep.properties().get("fixedValue") + " - ExecID:";

        // Минимальная ожидаемая длина (если UUID пустой, чего не бывает)
        assertTrue(storedLength >= 60, "Calculated length should be at least 60 characters (includes UUID)");

        // Эта проверка косвенно подтверждает, что:
        // 1. Source сработал и получил свойство.
        // 2. Executor передал данные от Source к Processor.
        // 3. Processor сработал и передал Integer к Sink.
        // 4. Sink успешно получил данные.
    }

    @Test
    @DisplayName("Should cleanup ThreadLocal context after execution")
    void shouldCleanupContext() {
        PipelineDefinition definition = new PipelineDefinition(
                "CleanupTest", "pipeline", "1.0", List.of(
                new StepDefinition("s1", "Source", Map.of(), null, null)
        )
        );
        registrar.registerPipeline(definition,beanDefinitionRegistry);

        // 1. Выполняем пайплайн
        executor.executePipeline(definition);

        // 2. Проверяем, что контекст пуст после cleanup
        assertNull(PipelineContextHolder.getContextId(),
                "Context ID must be null after cleanup.");
        assertTrue(PipelineContextHolder.getCurrentBeans().isEmpty(),
                "Bean map must be empty after cleanup.");
    }
}