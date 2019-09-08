package com.netflix.conductor.validations;

import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;

import javax.validation.Constraint;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import javax.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


import static com.netflix.conductor.common.metadata.workflow.TaskType.TASK_TYPE_SIMPLE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.TYPE;


/**
 * This constraint class validates following things.
 * 1. Check Task Def exists in DAO or not. If not check if it is ephemeral task type.
 */
@Documented
@Constraint(validatedBy = WorkflowTaskValidConstraint.WorkflowTaskValidValidator.class)
@Target({TYPE, FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface WorkflowTaskValidConstraint {
    String message() default "";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class WorkflowTaskValidValidator implements ConstraintValidator<WorkflowTaskValidConstraint, WorkflowTask> {

        @Override
        public void initialize(WorkflowTaskValidConstraint constraintAnnotation) {
        }

        @Override
        public boolean isValid(WorkflowTask workflowTask, ConstraintValidatorContext context) {
            context.disableDefaultConstraintViolation();

            if (workflowTask == null) {
                return true;
            }

            if (workflowTask.getName() == null) {
                String message = "WorkflowTask name cannot be empty or null";
                context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
            }

            boolean valid = true;

            // avoid task type definition check incase of non simple task
            if (!workflowTask.getType().equals(TASK_TYPE_SIMPLE)) {
                return valid;
            }

            if (ValidationContext.getMetadataDAO().getTaskDef(workflowTask.getName()) == null) {
                //check if task type is ephemeral
                TaskDef task = workflowTask.getTaskDefinition();
                if (task == null) {
                    valid = false;
                    String message = String.format("workflowTask: %s task definition is not defined", workflowTask.getName());
                    context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
                }
            }
            return valid;
        }
    }
}
