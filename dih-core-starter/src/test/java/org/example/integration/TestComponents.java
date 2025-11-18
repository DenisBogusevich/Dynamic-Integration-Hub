package org.example.integration;

import org.example.step.PipelineContext;
import org.example.step.PipelineStep;

// Вспомогательные классы для теста, можно объявить как static inner classes
public class TestComponents {

    /**
     * Source step: generates initial data (Void input -> String output).
     */
    static class StringTestSource implements PipelineStep<Object, String> {
        // Must be public for property injection (even if not used here)
        public String fixedValue = "Initial Data from Source";

        public void setFixedValue(String fixedValue) {
            this.fixedValue = fixedValue;
        }

        @Override
        public String execute(Object input, PipelineContext context) {
            // Источник игнорирует входные данные (input) и производит первые данные
            return fixedValue + " - ExecID:" + context.executionId();
        }
    }

    /**
     * Processor step: transforms data (String input -> String output).
     */
    static class StringLengthProcessor implements PipelineStep<String, Integer> {

        @Override
        public Integer execute(String input, PipelineContext context) {
            // Процессор берет String и возвращает его длину
            return input.length();
        }
    }

    /**
     * Sink step: consumes data (Integer input -> Void output).
     */
    static class DataStorageSink implements PipelineStep<Integer, Object> {
        // Статическая переменная для сохранения результата, чтобы тест мог его проверить
        public static Integer finalResult = null;

        @Override
        public Object execute(Integer input, PipelineContext context) {
            // Sink сохраняет результат и ничего не возвращает (null)
            finalResult = input;
            return null; // Нет выходных данных
        }
    }
}