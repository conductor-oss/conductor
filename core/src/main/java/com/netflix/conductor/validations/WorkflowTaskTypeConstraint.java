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
import java.text.ParseException;
import java.time.format.DateTimeParseException;
import java.util.Optional;

import javax.validation.Constraint;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import javax.validation.Payload;

import org.apache.commons.lang3.StringUtils;

import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.utils.DateTimeUtils;

import static com.netflix.conductor.core.execution.tasks.Terminate.getTerminationStatusParameter;
import static com.netflix.conductor.core.execution.tasks.Terminate.validateInputStatus;
import static com.netflix.conductor.core.execution.tasks.Wait.DURATION_INPUT;
import static com.netflix.conductor.core.execution.tasks.Wait.UNTIL_INPUT;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.TYPE;

/**
 * This constraint class validates following things. 1. Correct parameters are set depending on task
 * type.
 */
@Documented
@Constraint(validatedBy = WorkflowTaskTypeConstraint.WorkflowTaskValidator.class)
@Target({TYPE, ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface WorkflowTaskTypeConstraint {

    String message() default "";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class WorkflowTaskValidator
            implements ConstraintValidator<WorkflowTaskTypeConstraint, WorkflowTask> {

        final String PARAM_REQUIRED_STRING_FORMAT =
                "%s field is required for taskType: %s taskName: %s";

        @Override
        public void initialize(WorkflowTaskTypeConstraint constraintAnnotation) {}

        @Override
        public boolean isValid(WorkflowTask workflowTask, ConstraintValidatorContext context) {
            context.disableDefaultConstraintViolation();

            boolean valid = true;

            // depending on task type check if required parameters are set or not
            switch (workflowTask.getType()) {
                case TaskType.TASK_TYPE_EVENT:
                    valid = isEventTaskValid(workflowTask, context);
                    break;
                case TaskType.TASK_TYPE_DECISION:
                    valid = isDecisionTaskValid(workflowTask, context);
                    break;
                case TaskType.TASK_TYPE_SWITCH:
                    valid = isSwitchTaskValid(workflowTask, context);
                    break;
                case TaskType.TASK_TYPE_DYNAMIC:
                    valid = isDynamicTaskValid(workflowTask, context);
                    break;
                case TaskType.TASK_TYPE_FORK_JOIN_DYNAMIC:
                    valid = isDynamicForkJoinValid(workflowTask, context);
                    break;
                case TaskType.TASK_TYPE_HTTP:
                    valid = isHttpTaskValid(workflowTask, context);
                    break;
                case TaskType.TASK_TYPE_FORK_JOIN:
                    valid = isForkJoinTaskValid(workflowTask, context);
                    break;
                case TaskType.TASK_TYPE_TERMINATE:
                    valid = isTerminateTaskValid(workflowTask, context);
                    break;
                case TaskType.TASK_TYPE_KAFKA_PUBLISH:
                    valid = isKafkaPublishTaskValid(workflowTask, context);
                    break;
                case TaskType.TASK_TYPE_DO_WHILE:
                    valid = isDoWhileTaskValid(workflowTask, context);
                    break;
                case TaskType.TASK_TYPE_SUB_WORKFLOW:
                    valid = isSubWorkflowTaskValid(workflowTask, context);
                    break;
                case TaskType.TASK_TYPE_JSON_JQ_TRANSFORM:
                    valid = isJSONJQTransformTaskValid(workflowTask, context);
                    break;
                case TaskType.TASK_TYPE_WAIT:
                    valid = isWaitTaskValid(workflowTask, context);
                    break;
            }

            return valid;
        }

        private boolean isEventTaskValid(
                WorkflowTask workflowTask, ConstraintValidatorContext context) {
            boolean valid = true;
            if (workflowTask.getSink() == null) {
                String message =
                        String.format(
                                PARAM_REQUIRED_STRING_FORMAT,
                                "sink",
                                TaskType.TASK_TYPE_EVENT,
                                workflowTask.getName());
                context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
                valid = false;
            }
            return valid;
        }

        private boolean isDecisionTaskValid(
                WorkflowTask workflowTask, ConstraintValidatorContext context) {
            boolean valid = true;
            if (workflowTask.getCaseValueParam() == null
                    && workflowTask.getCaseExpression() == null) {
                String message =
                        String.format(
                                PARAM_REQUIRED_STRING_FORMAT,
                                "caseValueParam or caseExpression",
                                TaskType.DECISION,
                                workflowTask.getName());
                context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
                valid = false;
            }
            if (workflowTask.getDecisionCases() == null) {
                String message =
                        String.format(
                                PARAM_REQUIRED_STRING_FORMAT,
                                "decisionCases",
                                TaskType.DECISION,
                                workflowTask.getName());
                context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
                valid = false;
            } else if ((workflowTask.getDecisionCases() != null
                            || workflowTask.getCaseExpression() != null)
                    && (workflowTask.getDecisionCases().size() == 0)) {
                String message =
                        String.format(
                                "decisionCases should have atleast one task for taskType: %s taskName: %s",
                                TaskType.DECISION, workflowTask.getName());
                context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
                valid = false;
            }
            return valid;
        }

        private boolean isSwitchTaskValid(
                WorkflowTask workflowTask, ConstraintValidatorContext context) {
            boolean valid = true;
            if (workflowTask.getEvaluatorType() == null) {
                String message =
                        String.format(
                                PARAM_REQUIRED_STRING_FORMAT,
                                "evaluatorType",
                                TaskType.SWITCH,
                                workflowTask.getName());
                context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
                valid = false;
            } else if (workflowTask.getExpression() == null) {
                String message =
                        String.format(
                                PARAM_REQUIRED_STRING_FORMAT,
                                "expression",
                                TaskType.SWITCH,
                                workflowTask.getName());
                context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
                valid = false;
            }
            if (workflowTask.getDecisionCases() == null) {
                String message =
                        String.format(
                                PARAM_REQUIRED_STRING_FORMAT,
                                "decisionCases",
                                TaskType.SWITCH,
                                workflowTask.getName());
                context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
                valid = false;
            } else if (workflowTask.getDecisionCases() != null
                    && workflowTask.getDecisionCases().size() == 0) {
                String message =
                        String.format(
                                "decisionCases should have atleast one task for taskType: %s taskName: %s",
                                TaskType.SWITCH, workflowTask.getName());
                context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
                valid = false;
            }
            return valid;
        }

        private boolean isDoWhileTaskValid(
                WorkflowTask workflowTask, ConstraintValidatorContext context) {
            boolean valid = true;
            if (workflowTask.getLoopCondition() == null) {
                String message =
                        String.format(
                                PARAM_REQUIRED_STRING_FORMAT,
                                "loopExpression",
                                TaskType.DO_WHILE,
                                workflowTask.getName());
                context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
                valid = false;
            }
            if (workflowTask.getLoopOver() == null || workflowTask.getLoopOver().size() == 0) {
                String message =
                        String.format(
                                PARAM_REQUIRED_STRING_FORMAT,
                                "loopover",
                                TaskType.DO_WHILE,
                                workflowTask.getName());
                context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
                valid = false;
            }
            return valid;
        }

        private boolean isDynamicTaskValid(
                WorkflowTask workflowTask, ConstraintValidatorContext context) {
            boolean valid = true;
            if (workflowTask.getDynamicTaskNameParam() == null) {
                String message =
                        String.format(
                                PARAM_REQUIRED_STRING_FORMAT,
                                "dynamicTaskNameParam",
                                TaskType.DYNAMIC,
                                workflowTask.getName());
                context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
                valid = false;
            }

            return valid;
        }

        private boolean isWaitTaskValid(
                WorkflowTask workflowTask, ConstraintValidatorContext context) {
            boolean valid = true;
            String duration =
                    Optional.ofNullable(workflowTask.getInputParameters().get(DURATION_INPUT))
                            .orElse("")
                            .toString();
            String until =
                    Optional.ofNullable(workflowTask.getInputParameters().get(UNTIL_INPUT))
                            .orElse("")
                            .toString();

            if (StringUtils.isNotBlank(duration) && StringUtils.isNotBlank(until)) {
                String message =
                        "Both 'duration' and 'until' specified. Please provide only one input";
                context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
                valid = false;
            }

            try {
                if (StringUtils.isNotBlank(duration) && !(duration.startsWith("${"))) {
                    DateTimeUtils.parseDuration(duration);
                } else if (StringUtils.isNotBlank(until) && !(until.startsWith("${"))) {
                    DateTimeUtils.parseDate(until);
                }
            } catch (DateTimeParseException e) {
                String message = "Unable to parse date ";
                context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
                valid = false;
            } catch (IllegalArgumentException e) {
                String message = "Either date or duration is passed as null ";
                context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
                valid = false;
            } catch (ParseException e) {
                String message = "Unable to parse date ";
                context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
                valid = false;
            } catch (Exception e) {
                String message = "Wait time specified is invalid.  The duration must be in ";
                context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
                valid = false;
            }

            return valid;
        }

        private boolean isDynamicForkJoinValid(
                WorkflowTask workflowTask, ConstraintValidatorContext context) {
            boolean valid = true;

            // For DYNAMIC_FORK_JOIN_TASK support dynamicForkJoinTasksParam or combination of
            // dynamicForkTasksParam and dynamicForkTasksInputParamName.
            // Both are not allowed.
            if (workflowTask.getDynamicForkJoinTasksParam() != null
                    && (workflowTask.getDynamicForkTasksParam() != null
                            || workflowTask.getDynamicForkTasksInputParamName() != null)) {
                String message =
                        String.format(
                                "dynamicForkJoinTasksParam or combination of dynamicForkTasksInputParamName and dynamicForkTasksParam cam be used for taskType: %s taskName: %s",
                                TaskType.FORK_JOIN_DYNAMIC, workflowTask.getName());
                context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
                return false;
            }

            if (workflowTask.getDynamicForkJoinTasksParam() != null) {
                return valid;
            } else {
                if (workflowTask.getDynamicForkTasksParam() == null) {
                    String message =
                            String.format(
                                    PARAM_REQUIRED_STRING_FORMAT,
                                    "dynamicForkTasksParam",
                                    TaskType.FORK_JOIN_DYNAMIC,
                                    workflowTask.getName());
                    context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
                    valid = false;
                }
                if (workflowTask.getDynamicForkTasksInputParamName() == null) {
                    String message =
                            String.format(
                                    PARAM_REQUIRED_STRING_FORMAT,
                                    "dynamicForkTasksInputParamName",
                                    TaskType.FORK_JOIN_DYNAMIC,
                                    workflowTask.getName());
                    context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
                    valid = false;
                }
            }

            return valid;
        }

        private boolean isHttpTaskValid(
                WorkflowTask workflowTask, ConstraintValidatorContext context) {
            boolean valid = true;
            boolean isInputParameterSet = false;
            boolean isInputTemplateSet = false;

            // Either http_request in WorkflowTask inputParam should be set or in inputTemplate
            // Taskdef should be set
            if (workflowTask.getInputParameters() != null
                    && workflowTask.getInputParameters().containsKey("http_request")) {
                isInputParameterSet = true;
            }

            TaskDef taskDef =
                    Optional.ofNullable(workflowTask.getTaskDefinition())
                            .orElse(
                                    ValidationContext.getMetadataDAO()
                                            .getTaskDef(workflowTask.getName()));

            if (taskDef != null
                    && taskDef.getInputTemplate() != null
                    && taskDef.getInputTemplate().containsKey("http_request")) {
                isInputTemplateSet = true;
            }

            if (!(isInputParameterSet || isInputTemplateSet)) {
                String message =
                        String.format(
                                PARAM_REQUIRED_STRING_FORMAT,
                                "inputParameters.http_request",
                                TaskType.HTTP,
                                workflowTask.getName());
                context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
                valid = false;
            }

            return valid;
        }

        private boolean isForkJoinTaskValid(
                WorkflowTask workflowTask, ConstraintValidatorContext context) {
            boolean valid = true;

            if (workflowTask.getForkTasks() != null && (workflowTask.getForkTasks().size() == 0)) {
                String message =
                        String.format(
                                "forkTasks should have atleast one task for taskType: %s taskName: %s",
                                TaskType.FORK_JOIN, workflowTask.getName());
                context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
                valid = false;
            }

            return valid;
        }

        private boolean isTerminateTaskValid(
                WorkflowTask workflowTask, ConstraintValidatorContext context) {
            boolean valid = true;
            Object inputStatusParam =
                    workflowTask.getInputParameters().get(getTerminationStatusParameter());
            if (workflowTask.isOptional()) {
                String message =
                        String.format(
                                "terminate task cannot be optional, taskName: %s",
                                workflowTask.getName());
                context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
                valid = false;
            }
            if (inputStatusParam == null || !validateInputStatus(inputStatusParam.toString())) {
                String message =
                        String.format(
                                "terminate task must have an %s parameter and must be set to COMPLETED or FAILED, taskName: %s",
                                getTerminationStatusParameter(), workflowTask.getName());
                context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
                valid = false;
            }
            return valid;
        }

        private boolean isKafkaPublishTaskValid(
                WorkflowTask workflowTask, ConstraintValidatorContext context) {
            boolean valid = true;
            boolean isInputParameterSet = false;
            boolean isInputTemplateSet = false;

            // Either kafka_request in WorkflowTask inputParam should be set or in inputTemplate
            // Taskdef should be set
            if (workflowTask.getInputParameters() != null
                    && workflowTask.getInputParameters().containsKey("kafka_request")) {
                isInputParameterSet = true;
            }

            TaskDef taskDef =
                    Optional.ofNullable(workflowTask.getTaskDefinition())
                            .orElse(
                                    ValidationContext.getMetadataDAO()
                                            .getTaskDef(workflowTask.getName()));

            if (taskDef != null
                    && taskDef.getInputTemplate() != null
                    && taskDef.getInputTemplate().containsKey("kafka_request")) {
                isInputTemplateSet = true;
            }

            if (!(isInputParameterSet || isInputTemplateSet)) {
                String message =
                        String.format(
                                PARAM_REQUIRED_STRING_FORMAT,
                                "inputParameters.kafka_request",
                                TaskType.KAFKA_PUBLISH,
                                workflowTask.getName());
                context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
                valid = false;
            }

            return valid;
        }

        private boolean isSubWorkflowTaskValid(
                WorkflowTask workflowTask, ConstraintValidatorContext context) {
            boolean valid = true;
            if (workflowTask.getSubWorkflowParam() == null) {
                String message =
                        String.format(
                                PARAM_REQUIRED_STRING_FORMAT,
                                "subWorkflowParam",
                                TaskType.SUB_WORKFLOW,
                                workflowTask.getName());
                context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
                valid = false;
            }
            return valid;
        }

        private boolean isJSONJQTransformTaskValid(
                WorkflowTask workflowTask, ConstraintValidatorContext context) {
            boolean valid = true;
            boolean isInputParameterSet = false;
            boolean isInputTemplateSet = false;

            // Either queryExpression in WorkflowTask inputParam should be set or in inputTemplate
            // Taskdef should be set
            if (workflowTask.getInputParameters() != null
                    && workflowTask.getInputParameters().containsKey("queryExpression")) {
                isInputParameterSet = true;
            }

            TaskDef taskDef =
                    Optional.ofNullable(workflowTask.getTaskDefinition())
                            .orElse(
                                    ValidationContext.getMetadataDAO()
                                            .getTaskDef(workflowTask.getName()));

            if (taskDef != null
                    && taskDef.getInputTemplate() != null
                    && taskDef.getInputTemplate().containsKey("queryExpression")) {
                isInputTemplateSet = true;
            }

            if (!(isInputParameterSet || isInputTemplateSet)) {
                String message =
                        String.format(
                                PARAM_REQUIRED_STRING_FORMAT,
                                "inputParameters.queryExpression",
                                TaskType.JSON_JQ_TRANSFORM,
                                workflowTask.getName());
                context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
                valid = false;
            }

            return valid;
        }
    }
}
