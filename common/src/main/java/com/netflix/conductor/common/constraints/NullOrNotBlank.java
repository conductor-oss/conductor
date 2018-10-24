package com.netflix.conductor.common.constraints;

import javax.validation.Constraint;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import javax.validation.Payload;
import javax.validation.constraintvalidation.SupportedValidationTarget;
import javax.validation.constraintvalidation.ValidationTarget;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;

@Target({FIELD, PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Constraint(validatedBy = NullOrNotBlank.NullOrNotBlankValidator.class)
public @interface NullOrNotBlank {
    String message() default "{javax.validation.constraints.Pattern.message}";
    Class<?>[] groups() default { };
    Class<? extends Payload>[] payload() default {};

    @SupportedValidationTarget(ValidationTarget.PARAMETERS)
    class NullOrNotBlankValidator implements ConstraintValidator<NullOrNotBlank, String> {

        public void initialize(NullOrNotBlank parameters) {
        }

        public boolean isValid(String value, ConstraintValidatorContext constraintValidatorContext) {
            if (value == null) {
                return true;
            }
            if (value.length() == 0) {
                return false;
            }

            boolean allWhitespace = value.matches("^\\s*$");
            return !allWhitespace;
        }
    }
}
