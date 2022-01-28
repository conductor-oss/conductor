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

import com.google.common.base.Strings;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.TYPE;

/**
 * This constraint class validates that owner email is non-empty, but only if configuration says
 * owner email is mandatory.
 */
@Documented
@Constraint(validatedBy = OwnerEmailMandatoryConstraint.WorkflowTaskValidValidator.class)
@Target({TYPE, FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface OwnerEmailMandatoryConstraint {

    String message() default "ownerEmail cannot be empty";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class WorkflowTaskValidValidator
            implements ConstraintValidator<OwnerEmailMandatoryConstraint, String> {

        @Override
        public void initialize(OwnerEmailMandatoryConstraint constraintAnnotation) {}

        @Override
        public boolean isValid(String ownerEmail, ConstraintValidatorContext context) {
            return !ownerEmailMandatory || !Strings.isNullOrEmpty(ownerEmail);
        }

        private static boolean ownerEmailMandatory = true;

        public static void setOwnerEmailMandatory(boolean ownerEmailMandatory) {
            WorkflowTaskValidValidator.ownerEmailMandatory = ownerEmailMandatory;
        }
    }
}
