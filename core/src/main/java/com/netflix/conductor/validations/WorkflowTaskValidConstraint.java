/*
 * Copyright 2022 Netflix, Inc.
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
package com.netflix.conductor.validations;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import javax.validation.Constraint;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import javax.validation.Payload;

import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_SIMPLE;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.TYPE;

/**
 * This constraint class validates following things. 1. Check Task Def exists in DAO or not. If not,
 * check if it is ephemeral task type.
 */
@Documented
@Constraint(validatedBy = WorkflowTaskValidConstraint.WorkflowTaskValidValidator.class)
@Target({TYPE, FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface WorkflowTaskValidConstraint {

    String message() default "";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class WorkflowTaskValidValidator
            implements ConstraintValidator<WorkflowTaskValidConstraint, WorkflowTask> {

        @Override
        public void initialize(WorkflowTaskValidConstraint constraintAnnotation) {}

        @Override
        public boolean isValid(WorkflowTask workflowTask, ConstraintValidatorContext context) {
            context.disableDefaultConstraintViolation();

            if (workflowTask == null) {
                return true;
            }

            if (workflowTask.getName() == null) {
                String message = "WorkflowTask name cannot be empty or null";
                context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
            }

            boolean valid = true;

            // avoid task type definition check in case of non-simple task
            if (!workflowTask.getType().equals(TASK_TYPE_SIMPLE)) {
                return valid;
            }

            if (ValidationContext.getMetadataDAO().getTaskDef(workflowTask.getName()) == null) {
                // check if task type is ephemeral
                TaskDef task = workflowTask.getTaskDefinition();
                if (task == null) {
                    valid = false;
                    String message =
                            String.format(
                                    "workflowTask: %s task definition is not defined",
                                    workflowTask.getName());
                    context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
                }
            }
            return valid;
        }
    }
}
