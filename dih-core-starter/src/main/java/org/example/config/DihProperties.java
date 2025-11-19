package org.example.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dih.executor")
public class DihProperties {

    private String threadNamePrefix = "dih-vthread-";

    public String getThreadNamePrefix() { return threadNamePrefix; }
    public void setThreadNamePrefix(String threadNamePrefix) { this.threadNamePrefix = threadNamePrefix; }
}