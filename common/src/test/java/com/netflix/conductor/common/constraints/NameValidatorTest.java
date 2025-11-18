/*
 * Copyright 2024 Conductor Authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.common.constraints;

import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.validation.ConstraintValidatorContext;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class NameValidatorTest {
    @Test
    public void nameWithAllowedCharactersIsValid() {
        ValidNameConstraint.NameValidator nameValidator = new ValidNameConstraint.NameValidator();
        assertTrue(nameValidator.isValid("workflowDef", null));
    }

    @Test
    public void nonAllowedCharactersInNameIsInvalid() {
        ValidNameConstraint.NameValidator nameValidator = new ValidNameConstraint.NameValidator();
        ConstraintValidatorContext context = mock(ConstraintValidatorContext.class);
        ConstraintValidatorContext.ConstraintViolationBuilder builder =
                mock(ConstraintValidatorContext.ConstraintViolationBuilder.class);
        when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(builder);

        ReflectionTestUtils.setField(nameValidator, "nameValidationEnabled", true);

        assertFalse(nameValidator.isValid("workflowDef@", context));
    }

    // Null should be tested by @NotEmpty or @NotNull
    @Test
    public void nullIsValid() {
        ValidNameConstraint.NameValidator nameValidator = new ValidNameConstraint.NameValidator();
        assertTrue(nameValidator.isValid(null, null));
    }
}
