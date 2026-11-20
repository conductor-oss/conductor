/*
 * Copyright 2026 Conductor Authors.
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
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.test.context.support.TestPropertySourceUtils;

import jakarta.validation.ConstraintValidatorContext;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies {@link ValidNameConstraint.NameValidator}'s {@code @Value} placeholder resolves in a
 * context that does not define {@code conductor.app.workflow.name-validation.enabled}.
 *
 * <p>{@code NameValidator} ships in conductor-common, so Hibernate Validator instantiates it inside
 * every consuming application's context, not only conductor-server's. Without a default on the
 * placeholder, a consumer that does not define the property fails bean creation with a {@code
 * PlaceholderResolutionException}. That failure is lazy — it happens on the first request that
 * triggers bean validation, so it surfaces as a 500 on workflow/task registration in an application
 * that started up cleanly.
 *
 * <p>{@link NameValidatorTest} cannot catch this because it injects the field with {@link
 * org.springframework.test.util.ReflectionTestUtils}, bypassing placeholder resolution entirely.
 */
public class NameValidatorPlaceholderTest {

    @Test
    public void validatorIsConstructableWhenPropertyIsUndefined() {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext()) {
            context.registerBean(PropertySourcesPlaceholderConfigurer.class);
            context.register(ValidNameConstraint.NameValidator.class);
            context.refresh();

            ValidNameConstraint.NameValidator validator =
                    context.getBean(ValidNameConstraint.NameValidator.class);

            // Validation defaults to off, matching conductor-server's application.properties, so a
            // name that would otherwise be rejected passes.
            assertTrue(validator.isValid("workflowDef@", null));
        }
    }

    @Test
    public void explicitPropertyStillTakesEffect() {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext()) {
            TestPropertySourceUtils.addInlinedPropertiesToEnvironment(
                    context, "conductor.app.workflow.name-validation.enabled=true");
            context.registerBean(PropertySourcesPlaceholderConfigurer.class);
            context.register(ValidNameConstraint.NameValidator.class);
            context.refresh();

            ValidNameConstraint.NameValidator validator =
                    context.getBean(ValidNameConstraint.NameValidator.class);

            ConstraintValidatorContext validatorContext = mock(ConstraintValidatorContext.class);
            when(validatorContext.buildConstraintViolationWithTemplate(anyString()))
                    .thenReturn(mock(ConstraintValidatorContext.ConstraintViolationBuilder.class));

            // The default must not shadow an explicitly configured value.
            assertTrue(validator.isValid("workflowDef", null));
            assertFalse(validator.isValid("workflowDef@", validatorContext));
        }
    }
}
