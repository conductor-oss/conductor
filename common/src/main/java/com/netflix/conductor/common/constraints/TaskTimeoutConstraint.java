package com.netflix.conductor.common.constraints;

import com.netflix.conductor.common.metadata.tasks.TaskDef;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import javax.validation.Constraint;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import javax.validation.Payload;

import static java.lang.annotation.ElementType.TYPE;

/**
 *  This constraint checks for a given task responseTimeoutSeconds should be less than timeoutSeconds.
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
        public void initialize(TaskTimeoutConstraint constraintAnnotation) {
        }

        @Override
        public boolean isValid(TaskDef taskDef, ConstraintValidatorContext context) {
            context.disableDefaultConstraintViolation();

            boolean valid = true;

            if (taskDef.getTimeoutSeconds() > 0) {
                if (taskDef.getResponseTimeoutSeconds() > taskDef.getTimeoutSeconds()) {
                    valid = false;
                    String message = String.format("TaskDef: %s responseTimeoutSeconds: %d must be less than timeoutSeconds: %d",
                            taskDef.getName(), taskDef.getResponseTimeoutSeconds(), taskDef.getTimeoutSeconds());
                    context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
                }
            }

            return valid;
        }
    }
}