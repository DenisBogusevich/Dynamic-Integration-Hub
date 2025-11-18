package org.example.scope;

import org.example.step.PipelineContext;

import java.util.HashMap;
import java.util.Map;

public class PipelineContextHolder {

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
        THREAD_CONTEXT.remove(); // Очищаем контекст
    }

}
