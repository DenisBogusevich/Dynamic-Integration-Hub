package org.example.scope;

import org.example.config.DihCoreTestConfig;
import org.example.model.StepDefinition;
import org.example.registry.StepTypeRegistry;
import org.example.service.PipelineRegistrar;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.GenericApplicationContext;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = DihCoreTestConfig.class) // Используем нашу конфигурацию
class PipelineScopeIsolationTest {

    @Autowired
    private PipelineRegistrar pipelineRegistrar;

    @Autowired
    private StepTypeRegistry stepTypeRegistry;

    @Autowired
    private ApplicationContext context;
    @Autowired
    private GenericApplicationContext gcontext;

    private static final String STEP_ALIAS = "TestScopedStep";
    private static final String STEP_ID = "isolatedStep";

    // Тестовый класс с полем, которое мы будем менять, чтобы проверить изоляцию
    // Имеет Scope="pipeline", установленный регистратором.
    static class TestScopedStep {
        private final String instanceId = UUID.randomUUID().toString(); // Уникальный ID экземпляра
        public String getInstanceId() { return instanceId; }
        public String data = "initial"; // Поле для проверки состояния
        public void setData(String data) { this.data = data; }
    }

    @BeforeEach
    void setup() {
        // Регистрируем наш тестовый класс перед каждым тестом
        stepTypeRegistry.register(STEP_ALIAS, TestScopedStep.class);

        // Регистрируем бин, чтобы он существовал до выполнения тестов
        // ВАЖНО: PipelineRegistrar уже устанавливает scope="pipeline"
        pipelineRegistrar.registerStep("testIsolation",
                new StepDefinition(STEP_ID, STEP_ALIAS, Map.of(), null, null));
    }

    @AfterEach
    void cleanupTestRegistration() {
        String beanName = "testIsolation_" + STEP_ID;

        // Проверяем, что бин существует, прежде чем удалять
        if (gcontext.containsBeanDefinition(beanName)) {
            // Явно удаляем BeanDefinition из контекста Spring
            gcontext.removeBeanDefinition(beanName);
            System.out.println("Cleaned up bean registration: " + beanName);
        }
    }

    // ------------------------------------------------------------------------
    // 1. ПОСЛЕДОВАТЕЛЬНАЯ ИЗОЛЯЦИЯ (Sequential Isolation)
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("Should return different instances after context cleanup (Sequential Isolation)")
    void shouldReturnDifferentInstancesAfterCleanup() {
        final String beanName = "testIsolation_" + STEP_ID;

        // --- RUN 1 (Запуск 1) ---
        PipelineContextHolder.initializeContext("run-1"); // Инициализация контекста

        TestScopedStep step1 = (TestScopedStep) context.getBean(beanName);
        step1.setData("State from Run 1"); // Меняем состояние

        String instanceId1 = step1.getInstanceId();

        // Проверка: состояние должно быть сохранено в ThreadLocal
        assertEquals("State from Run 1", step1.data);

        // --- CLEANUP ---
        PipelineContextHolder.cleanup(); // Очищаем ThreadLocal

        // --- RUN 2 (Запуск 2) ---
        PipelineContextHolder.initializeContext("run-2"); // Инициализация нового контекста

        TestScopedStep step2 = (TestScopedStep) context.getBean(beanName);
        String instanceId2 = step2.getInstanceId();

        // Ассерт 1: Экземпляры должны быть разные (Изоляция)
        assertNotSame(step1, step2, "Instances must be different after cleanup.");
        assertNotEquals(instanceId1, instanceId2, "Instance IDs must be unique.");

        // Ассерт 2: Состояние должно быть сброшено (Чистый запуск)
        assertEquals("initial", step2.data, "State should be reset to initial state.");

        PipelineContextHolder.cleanup(); // Очистка после теста
    }

    // ------------------------------------------------------------------------
    // 2. ПАРАЛЛЕЛЬНАЯ ИЗОЛЯЦИЯ (Concurrent Isolation)
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("Should maintain separate state in concurrent threads")
    void shouldMaintainSeparateStateInConcurrentThreads() throws InterruptedException {
        final String beanName = "testIsolation_" + STEP_ID;
        final int numThreads = 5;
        CountDownLatch latch = new CountDownLatch(numThreads);

        // Используем Map для хранения уникальных InstanceID, полученных из разных потоков
        Map<String, String> instanceIds = new ConcurrentHashMap<>();

        var executor = Executors.newFixedThreadPool(numThreads);

        for (int i = 0; i < numThreads; i++) {
            final String threadId = "Thread-" + i;
            final String executionId = "exec-" + i;
            final String expectedState = "State from " + threadId;

            executor.submit(() -> {
                try {
                    // Инициализация контекста в НОВОМ потоке
                    PipelineContextHolder.initializeContext(executionId);

                    // 1. Получаем бин (Scope создаст новый экземпляр для этого потока)
                    TestScopedStep step = (TestScopedStep) context.getBean(beanName);

                    // 2. Меняем состояние (только для этого потока!)
                    step.setData(expectedState);

                    // 3. Проверяем, что состояние корректно сохранено
                    assertEquals(expectedState, step.data, "State mismatch in thread " + threadId);

                    // 4. Записываем ID экземпляра
                    instanceIds.put(threadId, step.getInstanceId());

                } catch (Exception e) {
                    fail("Exception occurred in thread " + threadId + ": " + e.getMessage());
                } finally {
                    PipelineContextHolder.cleanup(); // Обязательная очистка
                    latch.countDown();
                }
            });
        }

        // Ждем завершения всех потоков (до 5 секунд)
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Threads did not complete in time.");

        // Ассерт 1: Все потоки должны были получить уникальные экземпляры
        assertEquals(numThreads, instanceIds.size(), "Number of unique instances must equal number of threads.");

        // Ассерт 2: Проверка, что ни один ID не был повторно использован
        assertEquals(numThreads, instanceIds.values().stream().distinct().count(),
                "All instance IDs must be globally unique across all threads.");

        executor.shutdown();
    }
}