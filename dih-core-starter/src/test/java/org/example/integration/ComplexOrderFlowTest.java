package org.example.integration;

import org.example.config.DihCoreTestConfig;
import org.example.model.PipelineDefinition;
import org.example.model.RetryPolicyDefinition;
import org.example.model.StepDefinition;
import org.example.registry.StepTypeRegistry;
import org.example.service.PipelineExecutor;
import org.example.service.PipelineRegistrar;
import org.example.step.ParallelSplitterStep;
import org.example.step.PipelineContext;
import org.example.step.PipelineStep;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.GenericApplicationContext;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = DihCoreTestConfig.class)
@Import(TestComponents.class)
class ComplexOrderFlowTest {

    private static final String PIPELINE_NAME = "HighRiskOrderFlow";

    @Autowired private PipelineExecutor executor;
    @Autowired private StepTypeRegistry registry;

    // ==========================================
    // 1. Domain DTOs (Объекты данных)
    // ==========================================
    record OrderRequest(double amount, String userId) {}
    record RiskScore(int score, String status) {} // Результат проверки кредита
    record FraudResult(double probability) {}     // Результат проверки на фрод
    record OrderResult(String decision, String reason) {} // Итоговое решение

    // ==========================================
    // 2. Step Implementations (Шаги пайплайна)
    // ==========================================

    /** Шаг 1: Валидация (Вход: Map -> Выход: OrderRequest) */
    static class ValidationStep implements PipelineStep<Map<String, Object>, OrderRequest> {
        @Override
        public OrderRequest execute(Map<String, Object> input, PipelineContext context) {
            System.out.println("[Validation] Checking input...");
            if (!input.containsKey("amount") || !input.containsKey("userId")) {
                throw new IllegalArgumentException("Invalid order data");
            }
            return new OrderRequest((Double) input.get("amount"), (String) input.get("userId"));
        }
    }

    /** Ветка А: Кредитный скоринг (Нестабильный сервис!) */
    static class CreditCheckStep implements PipelineStep<OrderRequest, RiskScore> {
        // Статическое поле для теста, чтобы проверить работу Retry
        static int attemptCounter = 0;

        @Override
        public RiskScore execute(OrderRequest input, PipelineContext context) {
            attemptCounter++;
            System.out.println("[CreditCheck] Attempt #" + attemptCounter + " on thread " + Thread.currentThread().getName());

            // Эмулируем падение первые 2 раза
            if (attemptCounter < 3) {
                throw new RuntimeException("Credit Bureau Timeout (Simulated)");
            }
            return new RiskScore(750, "APPROVED");
        }
    }

    /** Ветка Б: Склад (Быстрая проверка) */
    static class InventoryStep implements PipelineStep<OrderRequest, Boolean> {
        @Override
        public Boolean execute(OrderRequest input, PipelineContext context) {
            System.out.println("[Inventory] Checking warehouse on thread " + Thread.currentThread().getName());
            return true; // Товар всегда есть
        }
    }

    /** Ветка В: Анти-фрод (Тяжелая ML модель) */
    static class FraudDetectionStep implements PipelineStep<OrderRequest, FraudResult> {
        @Override
        public FraudResult execute(OrderRequest input, PipelineContext context) {
            System.out.println("[AntiFraud] Analyzing patterns on thread " + Thread.currentThread().getName());
            try {
                Thread.sleep(100); // Имитация работы
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            return new FraudResult(0.05); // Низкий риск
        }
    }

    /** Шаг 3: Агрегатор решений (Собирает List результатов от Splitter) */
    static class DecisionAggregatorStep implements PipelineStep<List<Object>, OrderResult> {
        @Override
        public OrderResult execute(List<Object> input, PipelineContext context) {
            System.out.println("[Aggregator] Combining " + input.size() + " results...");

            // Важно: Порядок в списке соответствует порядку subSteps в JSON/Definition
            // 0: CreditCheck (RiskScore)
            // 1: Inventory (Boolean)
            // 2: Fraud (FraudResult)

            RiskScore credit = (RiskScore) input.get(0);
            Boolean inventory = (Boolean) input.get(1);
            FraudResult fraud = (FraudResult) input.get(2);

            if (inventory && credit.score > 600 && fraud.probability < 0.1) {
                return new OrderResult("ORDER_CONFIRMED", "All checks passed. ExecID: " + context.executionId());
            } else {
                return new OrderResult("ORDER_REJECTED", "High risk or no stock");
            }
        }
    }

    // ==========================================
    // 3. Test Configuration
    // ==========================================

    @BeforeEach
    void setup() {
        // Регистрируем наши классы в реестре движка
        registry.register("Validator", ValidationStep.class);
        registry.register("CreditCheck", CreditCheckStep.class);
        registry.register("Inventory", InventoryStep.class);
        registry.register("FraudCheck", FraudDetectionStep.class);
        registry.register("Aggregator", DecisionAggregatorStep.class);
        registry.register("ParallelSplitter", ParallelSplitterStep.class); // Стандартный шаг

        CreditCheckStep.attemptCounter = 0; // Сброс счетчика перед тестом
    }


    // ==========================================
    // 4. The MAIN Test
    // ==========================================

    @Test
    @DisplayName("E2E: Complex Flow with Retry, Parallel execution and Aggregation")
    void shouldProcessComplexOrder() {
        // --- 1. Собираем Definition (то, что обычно приходит из JSON) ---

        // 1.1. Валидация
        StepDefinition step1 = new StepDefinition("validate", "Validator", Map.of(), null, null);

        // 1.2. Ветки для параллельного исполнения
        StepDefinition branchCredit = new StepDefinition(
                "credit", "CreditCheck", Map.of(), null,
                new RetryPolicyDefinition(3, 50) // <--- Retry Policy! (3 попытки)
        );
        StepDefinition branchInventory = new StepDefinition("inventory", "Inventory", Map.of(), null, null);
        StepDefinition branchFraud = new StepDefinition("fraud", "FraudCheck", Map.of(), null, null);

        // 1.3. Сплиттер (Родитель веток)
        StepDefinition step2Splitter = new StepDefinition(
                "risk-analysis",
                "ParallelSplitter",
                Map.of("subStepIds", List.of("credit", "inventory", "fraud")), // ID должны совпадать!
                List.of(branchCredit, branchInventory, branchFraud), // Вложенные шаги
                null
        );

        // 1.4. Агрегатор
        StepDefinition step3Aggregator = new StepDefinition("aggregator", "Aggregator", Map.of(), null, null);

        // 1.5. Весь пайплайн
        PipelineDefinition pipelineDef = new PipelineDefinition(
                PIPELINE_NAME, "pipeline", "1.0",
                List.of(step1, step2Splitter, step3Aggregator)
        );


        // --- 3. Входные данные (эмуляция контроллера) ---
        Map<String, Object> initialInput = Map.of(
                "amount", 1500.00,
                "userId", "user-123-xyz"
        );

        // --- 4. ЗАПУСК! ---
        long start = System.currentTimeMillis();
        Object rawResult = executor.executePipeline(pipelineDef,initialInput); // Входные данные можно прокинуть через Source, тут упростили
        long duration = System.currentTimeMillis() - start;

        // --- 5. Проверки ---

        System.out.println("Pipeline finished in " + duration + "ms");

        // Проверка результата
        assertNotNull(rawResult);
        assertInstanceOf(OrderResult.class, rawResult);
        OrderResult result = (OrderResult) rawResult;

        assertEquals("ORDER_CONFIRMED", result.decision);
        assertTrue(result.reason.contains("ExecID:")); // Проверяем, что контекст пробросился

        // Проверка Retry: Должно быть 3 попытки (2 фейла + 1 успех)
        assertEquals(3, CreditCheckStep.attemptCounter, "Credit Step should retry exactly 3 times");

        System.out.println("FINAL RESULT: " + result);
    }
}