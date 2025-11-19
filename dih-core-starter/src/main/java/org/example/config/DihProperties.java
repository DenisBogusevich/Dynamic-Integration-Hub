package org.example.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed configuration properties for DIH Core.
 * Maps prefix "dih.executor" to this class fields.
 */
@ConfigurationProperties(prefix = "dih.executor")
public class DihProperties {

    // Значения по умолчанию
    private int corePoolSize = 10;
    private int maxPoolSize = 50;
    private int queueCapacity = 100;
    private String threadNamePrefix = "dih-worker-";

    // Getters and Setters (обязательны для ConfigurationProperties)

    public int getCorePoolSize() { return corePoolSize; }
    public void setCorePoolSize(int corePoolSize) { this.corePoolSize = corePoolSize; }

    public int getMaxPoolSize() { return maxPoolSize; }
    public void setMaxPoolSize(int maxPoolSize) { this.maxPoolSize = maxPoolSize; }

    public int getQueueCapacity() { return queueCapacity; }
    public void setQueueCapacity(int queueCapacity) { this.queueCapacity = queueCapacity; }

    public String getThreadNamePrefix() { return threadNamePrefix; }
    public void setThreadNamePrefix(String threadNamePrefix) { this.threadNamePrefix = threadNamePrefix; }
}