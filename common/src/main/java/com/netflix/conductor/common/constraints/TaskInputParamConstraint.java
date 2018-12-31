package com.netflix.conductor.common.constraints;

import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;

import javax.validation.Constraint;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import javax.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.netflix.conductor.common.utils.ConstraintParamUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableBoolean;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.TYPE;


/**
 * This constraint class validates following things.
 * 1. Check input params are in correct format
 */
@Documented
@Constraint(validatedBy = TaskInputParamConstraint.TaskInputParamValidator.class)
@Target({TYPE, FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface TaskInputParamConstraint {
    String message() default "";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class TaskInputParamValidator implements ConstraintValidator<TaskInputParamConstraint, Map<String, Object>> {

        @Override
        public void initialize(TaskInputParamConstraint constraintAnnotation) {
        }

        @Override
        public boolean isValid(Map<String, Object> inputParameters, ConstraintValidatorContext context) {
            context.disableDefaultConstraintViolation();
            if (inputParameters == null) {
                return true;
            }

            MutableBoolean valid = new MutableBoolean();
            valid.setValue(true);
            inputParameters.forEach((key, inputParam) -> {
                String paramPath = Objects.toString(inputParam, "");
                if (inputParam != null && StringUtils.isNotBlank(paramPath)) {
                    String[] paramPathComponents = ConstraintParamUtil.extractParamPathComponents(inputParam.toString());

                    if (paramPathComponents != null) {
                        for (String param : paramPathComponents) {
                            if (StringUtils.containsWhitespace(param)) {
                                valid.setValue(false);
                                String message = String.format("key: %s input parameter value: %s is not valid",
                                        key, paramPath);
                                context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
                            }
                        }
                    }
                } else {
                    valid.setValue(false);
                    String message = String.format("key: %s input parameter value: is null or empty", key);
                    context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
                }
            });

            return valid.getValue();
        }

    }
}
