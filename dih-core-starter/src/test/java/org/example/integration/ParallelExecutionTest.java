package org.example.integration;

import org.example.config.DihCoreTestConfig;
import org.example.exception.PipelineConcurrencyException;
import org.example.model.PipelineDefinition;
import org.example.model.StepDefinition;
import org.example.registry.StepTypeRegistry;
import org.example.service.PipelineExecutor;
import org.example.service.PipelineRegistrar;
import org.example.step.PipelineContext;
import org.example.step.PipelineStep;
import org.example.step.ParallelSplitterStep; // Наш новый класс
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.GenericApplicationContext;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = DihCoreTestConfig.class)
@Import(TestComponents.class)
class ParallelExecutionTest {

    private static final String PIPELINE_NAME = "ParallelFlow";

    @Autowired
    private PipelineRegistrar registrar;
    @Autowired
    private StepTypeRegistry registry;
    @Autowired
    private PipelineExecutor executor;
    @Autowired
    private GenericApplicationContext gcontext;

    // --- Тестовый компонент, имитирующий задержку ---
    static class SleepingStep implements PipelineStep<String, String> {
        private long sleepTime;
        private String resultValue;

        public void setSleepTime(long sleepTime) { this.sleepTime = sleepTime; }
        public void setResultValue(String resultValue) { this.resultValue = resultValue; }

        @Override
        public String execute(String input, PipelineContext context) throws Exception {
            // Проверка контекста внутри потока
            if (MDC.get("execution.id") == null) {
                throw new IllegalStateException("MDC Context is lost in thread " + Thread.currentThread().getName());
            }
            System.out.println(Thread.currentThread().getName() + " sleeping for " + sleepTime + "ms");
            Thread.sleep(sleepTime);
            return resultValue;
        }
    }

    static class FailingStep implements PipelineStep<String, String> {
        @Override
        public String execute(String input, PipelineContext context) {
            throw new RuntimeException("I am designed to fail!");
        }
    }

    @BeforeEach
    void setup() {
        registry.register("SleepingStep", SleepingStep.class);
        registry.register("ParallelSplitter", ParallelSplitterStep.class);
        registry.register("FailingStep", FailingStep.class);
    }

    @AfterEach
    void cleanup() {
        // Чистим контекст Spring от бинов пайплайна, чтобы тесты не мешали друг другу
        String[] beans = {
                PIPELINE_NAME + "_splitter",
                PIPELINE_NAME + "_branchA",
                PIPELINE_NAME + "_branchB",
                "FailTestFlow_splitter",
                "FailTestFlow_failBranch",
                "FailTestFlow_okBranch"
        };

        for (String bean : beans) {
            if (gcontext.containsBeanDefinition(bean)) {
                gcontext.removeBeanDefinition(bean);
            }
        }
    }

    @Test
    @DisplayName("Should execute branches in parallel and aggregate results")
    void shouldExecuteInParallelAndGatherResults() {
        // 1. Создаем две "ветки", каждая спит по 500мс.
        // Если бы выполнение было последовательным, тест занял бы 1000мс+.
        // При параллельном - около 500мс.

        StepDefinition branchA = new StepDefinition(
                "branchA", "SleepingStep",
                Map.of("sleepTime", 500L, "resultValue", "ResultA"),
                null, null
        );

        StepDefinition branchB = new StepDefinition(
                "branchB", "SleepingStep",
                Map.of("sleepTime", 500L, "resultValue", "ResultB"),
                null, null
        );

        // 2. Создаем Splitter, который запускает branchA и branchB
        // Важно: Мы передаем ID под-шагов через properties, так как наш ParallelSplitter ожидает 'subStepIds'
        StepDefinition splitter = new StepDefinition(
                "splitter", "ParallelSplitter",
                Map.of("subStepIds", List.of("branchA", "branchB")),
                List.of(branchA, branchB), // Чтобы регистратор создал бины для веток
                null
        );

        PipelineDefinition definition = new PipelineDefinition(
                PIPELINE_NAME, "pipeline", "1.0", List.of(splitter)
        );

        // 3. Регистрация
        registrar.registerPipeline(definition);

        // 4. Исполнение
        long start = System.currentTimeMillis();
        Object result = executor.executePipeline(definition);
        long duration = System.currentTimeMillis() - start;

        // 5. Проверки

        // Ассерт 1: Результат должен быть списком
        assertTrue(result instanceof List, "Result should be a List");
        List<?> resultList = (List<?>) result;

        // Ассерт 2: В списке должны быть результаты обеих веток
        assertEquals(2, resultList.size());
        assertTrue(resultList.contains("ResultA"));
        assertTrue(resultList.contains("ResultB"));

        // Ассерт 3: Проверка параллельности
        // С допуском на инициализацию потоков (например, < 900мс для двух задач по 500мс)
        System.out.println("Total execution time: " + duration + "ms");
        assertTrue(duration < 900, "Execution took too long (" + duration + "ms). Likely running sequentially instead of parallel.");
    }

    @Test
    @DisplayName("Should fail fast and throw PipelineConcurrencyException if one branch fails")
    void shouldFailWhenBranchFails() {
        // 1. Конфигурируем ветку, которая упадет (тип FailingStep)
        StepDefinition failBranch = new StepDefinition(
                "failBranch", "FailingStep", // <--- Ссылаемся на зарегистрированный тип
                Map.of(),
                null, null
        );

        // 2. Конфигурируем успешную ветку
        StepDefinition successBranch = new StepDefinition(
                "okBranch", "SleepingStep",
                Map.of("sleepTime", 100L, "resultValue", "OK"),
                null, null
        );

        StepDefinition splitter = new StepDefinition(
                "splitter", "ParallelSplitter",
                Map.of("subStepIds", List.of("failBranch", "okBranch")),
                List.of(failBranch, successBranch),
                null
        );

        PipelineDefinition definition = new PipelineDefinition(
                "FailTestFlow", "pipeline", "1.0", List.of(splitter)
        );

        registrar.registerPipeline(definition);

        // 3. Ожидаем наше кастомное исключение
        Exception exception = assertThrows(PipelineConcurrencyException.class, () -> {
            executor.executePipeline(definition);
        });

        System.out.println("Caught expected exception: " + exception.getMessage());
        // Проверка на то, что сообщение об ошибке адекватное
        assertTrue(exception.getMessage().contains("One or more parallel steps failed"));
    }
}