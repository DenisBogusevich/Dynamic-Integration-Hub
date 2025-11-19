package org.example.concurrency;

import org.example.scope.PipelineContextHolder;
import org.example.step.PipelineContext;
import org.springframework.core.task.TaskDecorator;

public class DihTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        // 1. Захват контекста из родительского потока (Capture)
        final PipelineContext capturedContext = PipelineContextHolder.getContext();

        // 2. Возврат обернутого Runnable (Wrapped)
        return () -> {
            try {
                // 3. Установка контекста в дочернем потоке (Restore)
                // Важно: нужно установить не только Context, но и пустые карты для бинов,
                // чтобы не было NullPointer при первом обращении к scope.get().
                if (capturedContext != null) {
                    PipelineContextHolder.initializeContext(capturedContext);
                    // Мы не копируем MAPS (бины), так как каждый параллельный шаг
                    // должен получить свой собственный, изолированный бин (благодаря Scope),
                    // но ему нужен общий execution ID.
                }

                runnable.run(); // Выполнение исходной задачи
            } finally {
                // 4. Очистка контекста дочернего потока (Cleanup)
                // Крайне важно для пула потоков!
                // Очищаем только Context ID, но не весь PipelineContextHolder.cleanup(),
                // поскольку он вызывает destruction callbacks, которые здесь не нужны
                // (они должны вызываться PipelineExecutor'ом).
                // А лучше: делаем точечное удаление контекста.
                PipelineContextHolder.cleanup();
            }
        };
    }
}
