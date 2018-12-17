package com.netflix.conductor.common.workflow;

import com.netflix.conductor.common.metadata.workflow.SubWorkflowParams;
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

public class SubWorkflowParamsTest {

    @Test
    public void testWorkflowTaskName() {
        SubWorkflowParams subWorkflowParams = new SubWorkflowParams();//name is null
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        Validator validator = factory.getValidator();

        Set<ConstraintViolation<Object>> result = validator.validate(subWorkflowParams);
        assertEquals(2, result.size());

        List<String> validationErrors = new ArrayList<>();
        result.forEach(e -> validationErrors.add(e.getMessage()));

        assertTrue(validationErrors.contains("SubWorkflowParams name cannot be null"));
        assertTrue(validationErrors.contains("SubWorkflowParams name cannot be empty"));
    }
}
