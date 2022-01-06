/*
 * Copyright 2020 Netflix, Inc.
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

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import javax.validation.Constraint;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import javax.validation.Payload;

import com.netflix.conductor.common.metadata.tasks.TaskDef;

import static java.lang.annotation.ElementType.TYPE;

/**
 * This constraint checks for a given task responseTimeoutSeconds should be less than
 * timeoutSeconds.
 */
@Documented
@Constraint(validatedBy = TaskTimeoutConstraint.TaskTimeoutValidator.class)
@Target({TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface TaskTimeoutConstraint {

    String message() default "";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class TaskTimeoutValidator implements ConstraintValidator<TaskTimeoutConstraint, TaskDef> {

        @Override
        public void initialize(TaskTimeoutConstraint constraintAnnotation) {}

        @Override
        public boolean isValid(TaskDef taskDef, ConstraintValidatorContext context) {
            context.disableDefaultConstraintViolation();

            boolean valid = true;

            if (taskDef.getTimeoutSeconds() > 0) {
                if (taskDef.getResponseTimeoutSeconds() > taskDef.getTimeoutSeconds()) {
                    valid = false;
                    String message =
                            String.format(
                                    "TaskDef: %s responseTimeoutSeconds: %d must be less than timeoutSeconds: %d",
                                    taskDef.getName(),
                                    taskDef.getResponseTimeoutSeconds(),
                                    taskDef.getTimeoutSeconds());
                    context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
                }
            }

            return valid;
        }
    }
}
