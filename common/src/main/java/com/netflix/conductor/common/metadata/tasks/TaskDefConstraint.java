package com.netflix.conductor.common.metadata.tasks;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import javax.validation.Constraint;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import javax.validation.Payload;

import static java.lang.annotation.ElementType.TYPE;

@Documented
@Constraint(validatedBy = TaskDefConstraint.TaskDefValidator.class)
@Target({TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface TaskDefConstraint {
    String message() default "";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class TaskDefValidator implements ConstraintValidator<TaskDefConstraint, TaskDef> {

        @Override
        public void initialize(TaskDefConstraint constraintAnnotation) {
        }

        @Override
        public boolean isValid(TaskDef taskDef, ConstraintValidatorContext context) {
            context.disableDefaultConstraintViolation();

            boolean valid = true;

            if (taskDef.getTimeoutSeconds() > 0) {
                if (taskDef.getResponseTimeoutSeconds() > taskDef.getTimeoutSeconds()) {
                    valid = false;
                    String message = String.format("TaskDef: %s responseTimeoutSeconds: %d <= timeoutSeconds: %d",
                            taskDef.getName(), taskDef.getResponseTimeoutSeconds(), taskDef.getTimeoutSeconds());
                    context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
                }
            }

            return valid;
        }
    }
}