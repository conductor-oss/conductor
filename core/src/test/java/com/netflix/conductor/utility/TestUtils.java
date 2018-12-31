package com.netflix.conductor.utility;

import javax.validation.ConstraintViolation;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class TestUtils {
    public static  Set<String> getConstraintViolationMessages(Set<ConstraintViolation<?>> constraintViolations) {
        Set<String> messages = new HashSet<>(constraintViolations.size());
        messages.addAll(constraintViolations.stream()
                .map(constraintViolation -> constraintViolation.getMessage())
                .collect(Collectors.toList()));
        return messages;
    }
}
