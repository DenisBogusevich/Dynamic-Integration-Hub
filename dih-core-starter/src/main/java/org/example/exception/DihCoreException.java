package org.example.exception;

// Наследуем от RuntimeException для unchecked-исключений
public abstract class DihCoreException extends RuntimeException {

    // Поле для контекстного логирования: имя пайплайна или ID шага
    private final String sourceName;

    // Конструктор 1: для обертывания другого исключения (e.g., IOException)
    public DihCoreException(String message, String sourceName, Throwable cause) {
        super(message, cause);
        this.sourceName = sourceName;
    }

    // Конструктор 2: для явных ошибок без внутреннего исключения
    public DihCoreException(String message, String sourceName) {
        super(message);
        this.sourceName = sourceName;
    }

    // Getter для SourceName — полезен для MDC и логирования
    public String getSourceName() {
        return sourceName;
    }
}