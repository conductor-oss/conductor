package com.netflix.conductor.validations;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.annotation.ElementType.TYPE;

/**
 * This constraint class validates following things.
 * 1. WorkflowDef is valid or not
 * 2. Make sure taskReferenceName used across different tasks are unique
 * 3. Verify inputParameters points to correct tasks or not
 */
@Documented
@Constraint(validatedBy = WorkflowDefConstraint.WorkflowDefValidator.class)
@Target({TYPE})
@Retention(RetentionPolicy.RUNTIME)
@interface WorkflowDefConstraint {
    String message() default "";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class WorkflowDefValidator implements ConstraintValidator<WorkflowDefConstraint, WorkflowDef> {

        @Override
        public void initialize(WorkflowDefConstraint constraintAnnotation) {
        }

        @Override
        public boolean isValid(WorkflowDef workflowDef, ConstraintValidatorContext context) {
            context.disableDefaultConstraintViolation();

            boolean valid = true;

            //check if taskReferenceNames are unique across tasks or not
            HashMap<String, Integer> taskReferenceMap = new HashMap<>();
            for (WorkflowTask workflowTask : workflowDef.getTasks()) {
                if (taskReferenceMap.containsKey(workflowTask.getTaskReferenceName())) {
                    valid = false;
                    String message = String.format("taskReferenceName: %s should be unique across tasks for a given workflowDefinition: %s",
                            workflowTask.getTaskReferenceName(), workflowDef.getName());
                    context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
                } else {
                    taskReferenceMap.put(workflowTask.getTaskReferenceName(), 1);
                }
            }

            //check inputParameters points to valid taskDef
            valid = verifyTaskInputParameters(context, workflowDef);

            return valid;
        }

        private boolean verifyTaskInputParameters(ConstraintValidatorContext context, WorkflowDef workflow) {
            final boolean[] valid = {true};

            if (workflow.getTasks() == null) {
                return valid[0];
            }

            workflow.getTasks()
                    .stream()
                    .filter(workflowTask -> workflow.getInputParameters() != null)
                    .forEach(workflowTask -> {

                        workflowTask.getInputParameters()
                                .forEach((key, value) -> {

                            String paramPath = "" + value;
                            Pattern pattern = Pattern.compile("^\\$\\{(.+)}$");
                            Matcher matcher = pattern.matcher(paramPath);

                            if (matcher.find()) {
                                String inputVariable = matcher.group(1);
                                String[] paramPathComponents = inputVariable.split("\\.");

                                String source = paramPathComponents[0];    //workflow, or task reference name
                                if (!"workflow".equals(source)) {
                                    WorkflowTask task = workflow.getTaskByRefName(source);
                                    if (task == null) {
                                        valid[0] = false;
                                        String message = String.format("taskDef: %s input parameter: %s value: %s is not valid",
                                                workflowTask.getName(), key, paramPath);
                                        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
                                    }
                                }
                            }
                        });
                    });

            return valid[0];
        }

    }
}