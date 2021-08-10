/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.conductor.common.constraints;

import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskDef.RetryLogic;

import javax.validation.Constraint;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import javax.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;

@Documented
@Constraint(validatedBy = RetryLogicConstraint.RetryLogicValidator.class)
@Target({TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RetryLogicConstraint {
  String message() default "";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};

  class RetryLogicValidator implements ConstraintValidator<RetryLogicConstraint, TaskDef> {

    @Override
    public void initialize(RetryLogicConstraint constraintAnnotation) {
    }

    @Override
    public boolean isValid(TaskDef taskDef, ConstraintValidatorContext context) {
      context.disableDefaultConstraintViolation();

      boolean valid = true;
      if (taskDef.getRetryLogic() == RetryLogic.UNSPECIFIED && taskDef.isRetryDelaySet()) {
        valid = false;
        String message = String.format("TaskDef: %s retryPolicy can't be UNSPECIFIED as retryDelay is set",
            taskDef.getName());
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
      }
      return valid;
    }
  }
}
