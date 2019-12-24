package com.netflix.conductor.common.constraints;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import javax.validation.Constraint;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import javax.validation.Payload;
import org.apache.commons.lang3.StringUtils;

/**
 *  This constraint checks semi-colon is not allowed in a given string.
 */
@Documented
@Constraint(validatedBy = NoSemiColonConstraint.NoSemiColonValidator.class)
@Target({FIELD, PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface NoSemiColonConstraint {
    String message() default "String: cannot contain the following set of characters: ':'";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class NoSemiColonValidator implements ConstraintValidator<NoSemiColonConstraint, String> {

        @Override
        public void initialize(NoSemiColonConstraint constraintAnnotation) {
        }

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            boolean valid = true;

            if (!StringUtils.isEmpty(value) && value.contains(":")) {
                valid = false;
            }

            return valid;
        }
    }
}
