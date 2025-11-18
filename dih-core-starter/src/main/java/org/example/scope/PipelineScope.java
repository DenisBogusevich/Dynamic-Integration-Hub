package org.example.scope;

import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.Scope;
import org.springframework.lang.Nullable;

/**
 * Custom Spring Scope implementation to manage the lifecycle of beans tied to a
 * single, synchronous execution of a Pipeline.
 *
 * <p>Beans registered under this scope are stored in a {@link ThreadLocal} map
 * managed by {@link PipelineContextHolder} to ensure isolation between parallel
 * pipeline runs on different threads.</p>
 */
public class PipelineScope implements Scope {
    /**
     * Retrieves the object from the current pipeline execution context.
     * If the bean does not yet exist for the current thread, the ObjectFactory is called
     * to create and store a new instance.
     *
     * @param name The unique name of the bean (e.g., 'pipelineId_stepId').
     * @param objectFactory The factory used by Spring to create the bean instance if not found.
     * @return The existing or newly created bean instance.
     */
    @Override
    public Object get(String name, ObjectFactory<?> objectFactory) {

        var beanMap = PipelineContextHolder.getCurrentBeans();

        if (beanMap.containsKey(name)) {
            return beanMap.get(name);
        }

        // Request Spring to create the bean instance
        Object bean = objectFactory.getObject();
        beanMap.put(name, bean);

        return bean;
    }

    /**
     * Removes the specified bean instance from the current pipeline context.
     * This method is usually called only during the cleanup phase of a pipeline execution.
     *
     * @param name The unique name of the bean to remove.
     * @return The removed bean instance, or {@code null} if no object was found.
     */
    @Override
    @Nullable
    public Object remove(String name) {
        // 1. Get the maps from the thread context
        var beanMap = PipelineContextHolder.getCurrentBeans();
        var callbackMap = PipelineContextHolder.getCurrentCallbacks();

        // 2. Safely retrieve the bean before removal
        Object removedBean = beanMap.remove(name);

        // 3. Execute the destruction callback if the bean was found
        if (removedBean != null) {
            Runnable callback = callbackMap.remove(name);
            if (callback != null) {
                // Important: Call destruction logic (e.g., close DB connections)
                callback.run();
            }
        }

        return removedBean;
    }

    /**
     * Registers a callback to be executed when the associated bean is removed from the scope.
     *
     * @param name The unique name of the bean.
     * @param callback The {@code Runnable} destruction callback.
     */
    @Override
    public void registerDestructionCallback(String name, Runnable callback) {
        var callbackMap = PipelineContextHolder.getCurrentCallbacks();
        // Simply store the callback associated with the bean's name
        callbackMap.put(name, callback);
    }

    /**
     * Resolves an object from the contextual environment (e.g., HTTP Session).
     * Not typically used in a pipeline scope, so it returns null.
     */
    @Override
    @Nullable
    public Object resolveContextualObject(String key) {
        return null;
    }

    /**
     * Returns the unique identifier for the current conversation/scope instance.
     * In this case, it returns the current pipeline execution ID.
     *
     * @return The current pipeline execution ID, or an empty string if not set.
     */
    @Override
    public String getConversationId() {
        String id = PipelineContextHolder.getContextId();
        // Return the execution ID, ensuring it's never null
        return id != null ? id : "";
    }
}
