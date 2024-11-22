/*
 * Copyright 2020 Conductor Authors.
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

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import static java.lang.annotation.ElementType.FIELD;

/**
 * This constraint class validates following things.
 *
 * <ul>
 *   <li>1. WorkflowDef is valid or not
 *   <li>2. Make sure taskReferenceName used across different tasks are unique
 *   <li>3. Verify inputParameters points to correct tasks or not
 * </ul>
 */
@Documented
@Constraint(validatedBy = NameValidator.class)
@Target({FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidNameConstraint {

    String message() default "";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
