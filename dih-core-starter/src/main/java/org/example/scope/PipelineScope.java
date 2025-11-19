package org.example.scope;

import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.Scope;
import org.springframework.lang.Nullable;

/**
 * <b>Legacy Component:</b> Formerly used to simulate request-scoped isolation in a shared context.
 *
 * @deprecated <b>Architectural Change:</b> We have moved to the <b>Ephemeral Child Context</b> pattern.
 * Steps are now registered as standard Singletons inside a dedicated, short-lived ApplicationContext.
 * This class is no longer used and should be deleted.
 */
@Deprecated(forRemoval = true)
public class PipelineScope implements Scope {

    @Override
    public Object get(String name, ObjectFactory<?> objectFactory) {
        throw new UnsupportedOperationException("PipelineScope is deprecated. Use Child Context isolation instead.");
    }

    @Override
    @Nullable
    public Object remove(String name) {
        return null;
    }

    @Override
    public void registerDestructionCallback(String name, Runnable callback) {
        // No-op
    }

    @Override
    @Nullable
    public Object resolveContextualObject(String key) {
        return null;
    }

    @Override
    public String getConversationId() {
        return "deprecated";
    }
}