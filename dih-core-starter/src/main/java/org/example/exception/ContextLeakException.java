package org.example.exception;

// Критическая ошибка, если ThreadLocal контекст потерян или установлен некорректно.
public class ContextLeakException extends DihCoreException {

    public ContextLeakException(String contextComponent) {
        super("Critical context leak or corruption detected. Context was null in: " + contextComponent, "DIH-Core");
    }
}