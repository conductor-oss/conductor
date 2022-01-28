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

import javax.validation.Constraint;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import javax.validation.Payload;

import org.apache.commons.lang3.StringUtils;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;

/** This constraint checks semi-colon is not allowed in a given string. */
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
        public void initialize(NoSemiColonConstraint constraintAnnotation) {}

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
