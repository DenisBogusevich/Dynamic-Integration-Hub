package org.example.scope;

import org.example.step.PipelineContext;
import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Map;

public class PipelineContextHolder {

    public static final String MDC_EXECUTION_ID = "execution.id";
    public static final String MDC_PIPELINE_NAME = "pipeline.name";

    private static final ThreadLocal<Map<String, Object>> THREAD_BEANS = ThreadLocal.withInitial(HashMap::new);
    private static final ThreadLocal<Map<String, Runnable>> THREAD_DESTRUCTION_CALLBACKS = ThreadLocal.withInitial(HashMap::new);
    private static final ThreadLocal<PipelineContext> THREAD_CONTEXT = new ThreadLocal<>();
    /**
     * Инициализирует контекст, устанавливая уникальный ID исполнения.
     * Обязателен при старте пайплайна, чтобы отличать его от других.
     *
     * @param executionId Уникальный ID текущего исполнения (например, UUID).
     */
    /**
     * Инициализирует контекст.
     * @param context Объект контекста текущего исполнения.
     */
    public static void initializeContext(PipelineContext context) {
        THREAD_CONTEXT.set(context);

        if (context != null) {
            MDC.put(MDC_EXECUTION_ID, context.executionId());
            MDC.put(MDC_PIPELINE_NAME, context.pipelineName());
        }
    }

    public static PipelineContext getContext() {
        return THREAD_CONTEXT.get();
    }

    public static Map<String, Object> getCurrentBeans(){
        return THREAD_BEANS.get();
    }
    /**
     * Возвращает карту коллбэков уничтожения, привязанную к текущему потоку.
     */
    public static Map<String, Runnable> getCurrentCallbacks() {
        return THREAD_DESTRUCTION_CALLBACKS.get();
    }

    public static String getContextId() {
        PipelineContext ctx = THREAD_CONTEXT.get();
        return ctx != null ? ctx.executionId() : null;
    }

    public static void removeContext() {
        THREAD_CONTEXT.remove();
        MDC.remove(MDC_EXECUTION_ID);
        MDC.remove(MDC_PIPELINE_NAME);
    }

    /**
     * Очищает контекст, вызывая все Destruction Callbacks и удаляя ссылки.
     * ДОЛЖЕН вызываться в блоке finally после завершения работы пайплайна.
     */
    public static void cleanup() {
        Map<String, Runnable> callbacks = THREAD_DESTRUCTION_CALLBACKS.get();
        if (callbacks != null) {
            callbacks.values().forEach(Runnable::run);
        }
        THREAD_BEANS.remove();
        THREAD_DESTRUCTION_CALLBACKS.remove();

        // removeContext() уже чистит THREAD_CONTEXT и MDC, но для надежности вызовем:
        removeContext();
        MDC.clear(); // Гарантированная полная очистка
    }

}
