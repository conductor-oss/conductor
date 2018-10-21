package com.netflix.conductor.common.metadata.workflow;

import com.netflix.conductor.common.metadata.tasks.TaskDef;

import javax.validation.Constraint;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import javax.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;

/**
 * This constraint class validates following things.
 * 1. TaskDef for given exists or not.
 * 2. Correct parameters are set depending on task type.
 * 3.
 */
@Documented
@Constraint(validatedBy = WorkflowTaskConstraint.WorkflowTaskValidator.class)
@Target({TYPE})
@Retention(RetentionPolicy.RUNTIME)
@interface WorkflowTaskConstraint {
    String message() default "";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class WorkflowTaskValidator implements ConstraintValidator<WorkflowTaskConstraint, WorkflowTask> {

        @Override
        public void initialize(WorkflowTaskConstraint constraintAnnotation) {
        }

        @Override
        public boolean isValid(WorkflowTask workflowTask, ConstraintValidatorContext context) {
            context.disableDefaultConstraintViolation();

            boolean valid = true;

            // depending on task type check if required parameters are set or not
            switch (workflowTask.getType()) {
                case TaskType.TASK_TYPE_SIMPLE:
                    valid = isSimpleTaskValid(workflowTask, context);
            }


            if (taskDef.getTimeoutSeconds() > 0) {
                if (taskDef.getResponseTimeoutSeconds() > taskDef.getTimeoutSeconds()) {
                    valid = false;
                    context.buildConstraintViolationWithTemplate( "TaskDef responseTimeoutSeconds <= timeoutSeconds" ).addConstraintViolation();
                }
            }

            return valid;
        }

        private boolean isSimpleTaskValid(WorkflowTask workflowTask, ConstraintValidatorContext context) {
            boolean valid = false;

            return valid;
        }
    }
}