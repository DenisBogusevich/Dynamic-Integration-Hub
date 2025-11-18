package org.example.exception;

public class StepTypeNotFoundException extends RuntimeException {
    public StepTypeNotFoundException(String type) {
        super("Unknown step type: '" + type + "'. Make sure it is registered in StepTypeRegistry.");
    }
}