package org.example.exception;

public class StepTypeNotFoundException extends PipelineConfigurationException {
    public StepTypeNotFoundException(String type) {
        super("Unknown step type: '" + type + "'. Make sure it is registered in StepTypeRegistry.");
    }
}