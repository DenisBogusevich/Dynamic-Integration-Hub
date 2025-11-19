package org.example.exception;

// Ошибки, связанные с регистрацией, Spring Beans или входной конфигурацией.
public class PipelineConfigurationException extends DihCoreException {

    // Используем "PipelineRegistrar" как sourceName по умолчанию для конфигурационных ошибок.
    private static final String DEFAULT_SOURCE = "PipelineRegistrar";

    public PipelineConfigurationException(String message, Throwable cause) {
        super(message, DEFAULT_SOURCE, cause);
    }

    public PipelineConfigurationException(String message) {
        super(message, DEFAULT_SOURCE);
    }
}