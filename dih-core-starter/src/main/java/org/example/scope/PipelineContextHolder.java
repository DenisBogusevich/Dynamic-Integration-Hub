package org.example.scope;

import java.util.HashMap;
import java.util.Map;

public class PipelineContextHolder {

    private static final ThreadLocal<Map<String, Object>> THREAD_BEANS = ThreadLocal.withInitial(HashMap::new);
    private static final ThreadLocal<Map<String, Runnable>> THREAD_DESTRUCTION_CALLBACKS = ThreadLocal.withInitial(HashMap::new);
    private static final ThreadLocal<String> THREAD_CONTEXT_ID = new ThreadLocal<>();

    /**
     * Инициализирует контекст, устанавливая уникальный ID исполнения.
     * Обязателен при старте пайплайна, чтобы отличать его от других.
     *
     * @param executionId Уникальный ID текущего исполнения (например, UUID).
     */
    public static void initializeContext(String executionId) {
        // THREAD_BEANS и CALLBACKS инициализируются через withInitial() при первом get().

        // Устанавливаем ID контекста, если он не установлен
        if (THREAD_CONTEXT_ID.get() == null) {
            THREAD_CONTEXT_ID.set(executionId);
        }
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
        return THREAD_CONTEXT_ID.get();
    }

    public static void setContextId(String contextId) {
        THREAD_CONTEXT_ID.set(contextId);
    }

    /**
     * Очищает контекст, вызывая все Destruction Callbacks и удаляя ссылки.
     * ДОЛЖЕН вызываться в блоке finally после завершения работы пайплайна.
     */
    public static void cleanup() {
        // 1. Вызываем все коллбэки уничтожения
        Map<String, Runnable> callbacks = THREAD_DESTRUCTION_CALLBACKS.get();
        if (callbacks != null) {
            callbacks.values().forEach(Runnable::run);
        }

        // 2. Удаляем все ThreadLocal переменные
        // Это предотвращает утечки памяти и протечку состояния в другие задачи потока.
        THREAD_BEANS.remove();
        THREAD_DESTRUCTION_CALLBACKS.remove();
        THREAD_CONTEXT_ID.remove();
    }

}
