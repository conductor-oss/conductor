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
import java.util.HashMap;
import java.util.List;

import javax.validation.Constraint;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import javax.validation.Payload;

import org.apache.commons.lang3.mutable.MutableBoolean;

import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.utils.ConstraintParamUtil;

import static java.lang.annotation.ElementType.TYPE;

/**
 * This constraint class validates following things.
 *
 * <ul>
 *   <li>1. WorkflowDef is valid or not
 *   <li>2. Make sure taskReferenceName used across different tasks are unique
 *   <li>3. Verify inputParameters points to correct tasks or not
 * </ul>
 */
@Documented
@Constraint(validatedBy = TaskReferenceNameUniqueConstraint.TaskReferenceNameUniqueValidator.class)
@Target({TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface TaskReferenceNameUniqueConstraint {

    String message() default "";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class TaskReferenceNameUniqueValidator
            implements ConstraintValidator<TaskReferenceNameUniqueConstraint, WorkflowDef> {

        @Override
        public void initialize(TaskReferenceNameUniqueConstraint constraintAnnotation) {}

        @Override
        public boolean isValid(WorkflowDef workflowDef, ConstraintValidatorContext context) {
            context.disableDefaultConstraintViolation();

            boolean valid = true;

            // check if taskReferenceNames are unique across tasks or not
            HashMap<String, Integer> taskReferenceMap = new HashMap<>();
            for (WorkflowTask workflowTask : workflowDef.collectTasks()) {
                if (taskReferenceMap.containsKey(workflowTask.getTaskReferenceName())) {
                    String message =
                            String.format(
                                    "taskReferenceName: %s should be unique across tasks for a given workflowDefinition: %s",
                                    workflowTask.getTaskReferenceName(), workflowDef.getName());
                    context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
                    valid = false;
                } else {
                    taskReferenceMap.put(workflowTask.getTaskReferenceName(), 1);
                }
            }
            // check inputParameters points to valid taskDef
            return valid & verifyTaskInputParameters(context, workflowDef);
        }

        private boolean verifyTaskInputParameters(
                ConstraintValidatorContext context, WorkflowDef workflow) {
            MutableBoolean valid = new MutableBoolean();
            valid.setValue(true);

            if (workflow.getTasks() == null) {
                return valid.getValue();
            }

            workflow.getTasks().stream()
                    .filter(workflowTask -> workflowTask.getInputParameters() != null)
                    .forEach(
                            workflowTask -> {
                                List<String> errors =
                                        ConstraintParamUtil.validateInputParam(
                                                workflowTask.getInputParameters(),
                                                workflowTask.getName(),
                                                workflow);
                                errors.forEach(
                                        message ->
                                                context.buildConstraintViolationWithTemplate(
                                                                message)
                                                        .addConstraintViolation());
                                if (errors.size() > 0) {
                                    valid.setValue(false);
                                }
                            });

            return valid.getValue();
        }
    }
}
