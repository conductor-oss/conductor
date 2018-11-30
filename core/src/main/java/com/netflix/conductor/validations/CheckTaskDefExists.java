package com.netflix.conductor.validations;

import javax.validation.Constraint;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import javax.validation.Payload;
import javax.validation.constraints.NotNull;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE;


@Documented
@Constraint(validatedBy = CheckTaskDefExists.TaskUniqueValidator.class)
@Target({TYPE, METHOD, PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface CheckTaskDefExists {
    String message() default "";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class TaskUniqueValidator implements ConstraintValidator<CheckTaskDefExists, String> {

        @Override
        public void initialize(CheckTaskDefExists constraintAnnotation) {
        }

        @Override
        public boolean isValid(String taskDefName, ConstraintValidatorContext context) {
            if (taskDefName == null) {
                return true;
            }

            context.disableDefaultConstraintViolation();

            boolean valid = true;

            if (ValidationContext.getMetadataDAO().getTaskDef(taskDefName) == null) {
                valid = false;
                String message = String.format("taskDef by name: %s does not exists", taskDefName);
                context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
            }
            return valid;
        }
    }
}
