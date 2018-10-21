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
 * 1. WorkflowDef is valid or not
 * 2. Make sure taskReferenceName used across different tasks are unique
 * 3. Verify inputParameters points to correct tasks or not
 */
@Documented
@Constraint(validatedBy = WorkflowDefConstraint.WorkflowDefValidator.class)
@Target({TYPE})
@Retention(RetentionPolicy.RUNTIME)
@interface WorkflowDefConstraint {
    String message() default "";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class WorkflowDefValidator implements ConstraintValidator<WorkflowDefConstraint, WorkflowDef> {

        @Override
        public void initialize(WorkflowDefConstraint constraintAnnotation) {
        }

        @Override
        public boolean isValid(WorkflowDef workflowDef, ConstraintValidatorContext context) {
            context.disableDefaultConstraintViolation();

            boolean valid = true;

            if (taskDef.getTimeoutSeconds() > 0) {
                if (taskDef.getResponseTimeoutSeconds() > taskDef.getTimeoutSeconds()) {
                    valid = false;
                    context.buildConstraintViolationWithTemplate( "TaskDef responseTimeoutSeconds <= timeoutSeconds" ).addConstraintViolation();
                }
            }

            return valid;
        }
    }
}