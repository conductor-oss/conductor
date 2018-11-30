package com.netflix.conductor.validations;

import javax.validation.Constraint;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import javax.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE;


@Documented
@Constraint(validatedBy = CheckTaskDefNotExists.TaskUniqueValidator.class)
@Target({TYPE, METHOD, PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface CheckTaskDefNotExists {
    String message() default "";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class TaskUniqueValidator implements ConstraintValidator<CheckTaskDefNotExists, String> {

        @Override
        public void initialize(CheckTaskDefNotExists constraintAnnotation) {
        }

        @Override
        public boolean isValid(String taskDefName, ConstraintValidatorContext context) {
            if (taskDefName == null) {
                return true;
            }

            context.disableDefaultConstraintViolation();

            boolean valid = true;

            if (ValidationContext.getMetadataDAO().getTaskDef(taskDefName) != null) {
                valid = false;
                context.buildConstraintViolationWithTemplate("taskDef by name already exists").addConstraintViolation();
            }
            return valid;
        }
    }
}
