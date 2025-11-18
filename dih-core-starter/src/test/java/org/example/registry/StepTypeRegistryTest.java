package org.example.registry;

import org.example.exception.StepTypeNotFoundException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.Assert;


public class StepTypeRegistryTest {

    @Test
    public void getExistingClass() {
        StepTypeRegistry stepTypeRegistry = new StepTypeRegistry();
        stepTypeRegistry.register("text", String.class);

        Assertions.assertEquals(String.class, stepTypeRegistry.getStepClass("text"));
    }

    @Test
    public void getNoneExistingClass() {
        StepTypeRegistry stepTypeRegistry = new StepTypeRegistry();
        stepTypeRegistry.register("text", String.class);

        Assertions.assertThrows(
                StepTypeNotFoundException.class,
                () -> stepTypeRegistry.getStepClass("None")
        );
    }


}
