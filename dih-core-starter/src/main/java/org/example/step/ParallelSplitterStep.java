package org.example.step;

import org.example.model.StepDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ResolvableType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

// Предполагаем, что StepDefinition для этого шага инжектируется через properties
// или мы получаем контекст, чтобы найти суб-шаги.
public class ParallelSplitterStep<I, O> implements PipelineStep<I, O> {

    @Autowired
    private ApplicationContext springContext;
    @Autowired // Инжектируем наш настроенный Executor
    private ThreadPoolTaskExecutor dihTaskExecutor;

    // ВАЖНО: Эта логика предполагает, что ParallelSplitterStep
    // получает список ID шагов или StepDefinition через property injection.
    // Для этого нужно будет доработать PipelineRegistrar/StepDefinition.
    public List<String> subStepIds; // Например, инжектируемое свойство

    public void setSubStepIds(List<String> subStepIds) {
        this.subStepIds = subStepIds;
    }

    @Override
    public O execute(I input, PipelineContext pipelineContext) throws Exception {

        if (subStepIds == null || subStepIds.isEmpty()) {
            System.out.println("No sub-steps defined for ParallelSplitter. Exiting.");
            return null; // или возвращаем входной объект
        }

        // 1. Создание списка задач CompletableFuture
        String pipelineName = pipelineContext.pipelineName(); // Используем обновленный Context

        // 1. Создание списка задач CompletableFuture
        List<CompletableFuture<Object>> futures = subStepIds.stream()
                .map(stepId -> {
                    String beanName = pipelineName + "_" + stepId;

                    // 2. Получаем бин из Spring ApplicationContext!
                    // Используем springContext.getBean(name)
                    Object subStepBean = springContext.getBean(beanName);

                    // Проверка типа (Defensive Programming)
                    if (!(subStepBean instanceof PipelineStep)) {
                        throw new IllegalStateException("Bean '" + beanName + "' is not a PipelineStep.");
                    }

                    // Безопасное приведение типа для исполнения
                    PipelineStep<Object, Object> subStep = (PipelineStep<Object, Object>) subStepBean;
                    // 3. Запускаем асинхронную задачу
                    return CompletableFuture.supplyAsync(() -> {
                        try {
                            // TaskDecorator прокинет контекст. Передаем PipelineContext как аргумент.
                            return subStep.execute(input, pipelineContext);
                        } catch (Exception e) {
                            throw new RuntimeException("Parallel step failed: " + stepId, e);
                        }
                    }, dihTaskExecutor);
                })
                .toList();

        // 4. Ожидание завершения всех задач (DIH-404)
        CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

        allOf.get(); // Блокировка

        // 5. Агрегация результатов (TODO: Реализовать логику агрегации, сейчас просто null)
        return null;

    }
}