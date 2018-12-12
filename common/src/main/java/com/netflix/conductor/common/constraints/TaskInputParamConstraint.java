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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;

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

            final boolean[] valid = {true};

            inputParameters.forEach((key, value) -> {

                        String paramPath = "" + value;

                        if (StringUtils.isNotBlank(paramPath)) {
                            Pattern pattern = Pattern.compile("^\\$\\{(.+)}$");
                            Matcher matcher = pattern.matcher(paramPath);

                            if (matcher.find()) {
                                String inputVariable = matcher.group(1);
                                String[] paramPathComponents = inputVariable.split("\\.");

                                for (String param: paramPathComponents) {
                                    if(StringUtils.containsWhitespace(param)) {
                                        valid[0] = false;
                                        String message = String.format("key: %s input parameter value: %s is not valid",
                                                key, paramPath);
                                        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
                                    }
                                }
                            }
                        } else {
                            valid[0] = false;
                            String message = String.format("key: %s input parameter value: is null or empty", key);
                            context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
                        }
                    });

            return valid[0];
        }

    }
}
