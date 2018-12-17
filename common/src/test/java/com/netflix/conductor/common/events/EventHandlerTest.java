package com.netflix.conductor.common.events;

import com.netflix.conductor.common.metadata.events.EventHandler;
import org.junit.Test;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EventHandlerTest {

    @Test
    public void testWorkflowTaskName() {
        EventHandler taskDef = new EventHandler();//name is null

        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        Validator validator = factory.getValidator();
        Set<ConstraintViolation<Object>> result = validator.validate(taskDef);
        assertEquals(3, result.size());

        List<String> validationErrors = new ArrayList<>();
        result.forEach(e -> validationErrors.add(e.getMessage()));

        assertTrue(validationErrors.contains("Missing event handler name"));
        assertTrue(validationErrors.contains("Missing event location"));
        assertTrue(validationErrors.contains("No actions specified. Please specify at-least one action"));
    }
}
