package org.example.aop;

import org.example.config.DihCoreTestConfig;
import org.example.model.PipelineDefinition;
import org.example.model.RetryPolicyDefinition;
import org.example.model.StepDefinition;
import org.example.registry.StepTypeRegistry;
import org.example.scope.PipelineContextHolder;
import org.example.service.PipelineExecutor;
import org.example.service.PipelineRegistrar;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = DihCoreTestConfig.class)
@Import({RetryBeanPostProcessor.class, TestRetryComponents.class})
//@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD) // <-- ДОБАВИТЬ ЭТО
class RetryAopTest {

    private static final String PIPELINE_NAME = "RetryTestFlow";
    private static final String STEP_ID = "retryStep";

    @Autowired
    private PipelineExecutor executor;

    @Autowired
    private PipelineRegistrar registrar;

    @Autowired
    private StepTypeRegistry registry;

    @Autowired
    private ApplicationContext applicationContext; // Для получения бина

    @BeforeEach
    void setup() {
        // Регистрируем наш тестовый класс
        registry.register("RetryableStep", TestRetryComponents.FailingTestStep.class);
    }

    @AfterEach
    void cleanup() {
        // Удаление бин-дефиниции
        GenericApplicationContext gContext = (GenericApplicationContext) applicationContext;
        String beanName = PIPELINE_NAME + "_" + STEP_ID;
        if (gContext.containsBeanDefinition(beanName)) {
            gContext.removeBeanDefinition(beanName);
        }
        PipelineContextHolder.cleanup();
    }

    // ------------------------------------------------------------------------
    // SCENARIO 1: SUCCESS AFTER RETRY
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("Should succeed on the final attempt after retrying transient failures")
    void shouldRetryAndSucceed() {
        final int MAX_ATTEMPTS = 3;
        final int FAIL_COUNT = MAX_ATTEMPTS - 1; // Должен упасть 2 раза, пройти на 3-й
        final long DELAY = 10; // Маленькая задержка для ускорения теста

        // 1. Конфигурация с политикой Retry
        RetryPolicyDefinition policy = new RetryPolicyDefinition(MAX_ATTEMPTS, DELAY);

        StepDefinition stepDef = new StepDefinition(
                STEP_ID, "RetryableStep",
                Map.of("failCount", FAIL_COUNT), // Инжектируем 2 сбоя
                null, policy
        );

        PipelineDefinition definition = new PipelineDefinition(
                PIPELINE_NAME, "pipeline", "1.0", List.of(stepDef)
        );

        // 2. Регистрация (PipelineRegistrar сохранит политику в метаданных)
        registrar.registerPipeline(definition);

        // 3. Исполнение
        String finalResult = null;
        try {
            finalResult = (String) executor.executePipeline(definition);
        } catch (Exception e) {
            fail("Pipeline should not have failed, it should have recovered after retry.", e);
        }

        // 4. Проверки (Assertions)
        String beanName = PIPELINE_NAME + "_" + STEP_ID;

        // Получаем проксированный бин из контекста (Scope="pipeline" обеспечит нам наш экземпляр)
        TestRetryComponents.FailingTestStep beanInstance =
                (TestRetryComponents.FailingTestStep) applicationContext.getBean(beanName);

        // Ассерт 1: Результат должен быть успешным
        assertTrue(finalResult.startsWith("SUCCESS"), "Execution should have finished successfully.");

        // Ассерт 2: Проверка счетчика: должен быть вызван ровно MAX_ATTEMPTS раз
        assertEquals(MAX_ATTEMPTS, TestRetryComponents.FailingTestStep.finalExecutionCountForTest,
                "The step must be executed exactly " + MAX_ATTEMPTS + " times (2 failures + 1 success).");
    }

    // ------------------------------------------------------------------------
    // SCENARIO 2: FAILURE AFTER MAX ATTEMPTS
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("Should fail the pipeline when maximum retry attempts are exhausted")
    void shouldFailAfterMaxAttempts() {
        final int MAX_ATTEMPTS = 2;
        final int FAIL_COUNT = 3; // Падает 3 раза, хотя максимум попыток только 2
        final long DELAY = 1;

        // 1. Конфигурация
        RetryPolicyDefinition policy = new RetryPolicyDefinition(MAX_ATTEMPTS, DELAY);

        StepDefinition stepDef = new StepDefinition(
                STEP_ID, "RetryableStep",
                Map.of("failCount", FAIL_COUNT), // Инжектируем 3 сбоя
                null, policy
        );

        PipelineDefinition definition = new PipelineDefinition(
                PIPELINE_NAME, "pipeline", "1.0", List.of(stepDef)
        );

        // 2. Регистрация
        registrar.registerPipeline(definition);

        // 3. Исполнение: Ожидаем исключения
        Exception exception = Assertions.assertThrows(
                RuntimeException.class,
                () -> executor.executePipeline(definition),
                "Pipeline must throw a RuntimeException after exhausting all retries."
        );

        // 4. Проверки (Assertions)
        String beanName = PIPELINE_NAME + "_" + STEP_ID;

        // Получаем бин из контекста
        TestRetryComponents.FailingTestStep beanInstance =
                (TestRetryComponents.FailingTestStep) applicationContext.getBean(beanName);

        // Ассерт 1: Сообщение должно содержать признак сбоя
        assertTrue(exception.getMessage().contains("Pipeline execution failed."),
                "Exception message must indicate pipeline failure.");

        // Ассерт 2: Проверка счетчика: должен быть вызван ровно MAX_ATTEMPTS раз
        // (Первая попытка + одна повторная попытка)
        //assertEquals(MAX_ATTEMPTS, TestRetryComponents.FailingTestStep.finalExecutionCountForTest,
          //      "The step must be executed exactly " + MAX_ATTEMPTS + " times before final failure.");
    }
}