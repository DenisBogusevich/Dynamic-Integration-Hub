package org.example.aop;

import org.example.step.PipelineContext;
import org.example.step.PipelineStep;

// Вспомогательный класс для теста
public class TestRetryComponents {

    /**
     * PipelineStep that fails N times before succeeding.
     * The internal state (executionCount) must be protected by the PipelineScope
     * to ensure isolation between concurrent tests.
     */
    static class FailingTestStep implements PipelineStep<String, String> {

        // Поле, инжектируемое через StepDefinition (количество неудачных попыток)
        private int failCount;

        // Внутренний счетчик, который будет изолирован PipelineScope
        private int executionCount = 0;
        public static int finalExecutionCountForTest = 0;

        // Сеттер для property injection
        public void setFailCount(int failCount) {
            this.failCount = failCount;
        }

        public int getExecutionCount() {
            return executionCount;
        }

        @Override
        public String execute(String input, PipelineContext context) throws Exception {
            executionCount++;

            System.out.println("-> Attempt #" + executionCount + " (Fail limit: " + failCount + ")");

            if (executionCount <= failCount) {
                // Имитируем временный сбой (например, таймаут)
                throw new RuntimeException("Simulated transient failure on attempt " + executionCount);
            }
            finalExecutionCountForTest = executionCount;

            // Успешное завершение
            return "SUCCESS after " + executionCount + " attempts.";
        }
    }
}