package com.netflix.conductor.validations;

import com.google.common.base.Preconditions;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;

import javax.validation.Constraint;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import javax.validation.Payload;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.HashMap;
import java.util.Map;

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
            for(WorkflowTask workflowTask: workflowDef.getTasks()) {
                if(taskReferenceMap.containsKey(workflowTask.getTaskReferenceName())){
                    valid = false;
                    context.buildConstraintViolationWithTemplate( "taskDef by name already exists" ).addConstraintViolation();
                } else {
                    taskReferenceMap.put(workflowTask.getTaskReferenceName(), 1);
                }
            }

            //check inputParameters points to valid taskDef

            for(WorkflowTask workflowTask: workflowDef.getTasks()) {
                valid = verifyTaskInput(workflowDef, workflowTask.getInputParameters());
                if(!valid){
                    String message = String.format("taskDef %s inputparameters not valid", workflowTask.getName());
                    context.buildConstraintViolationWithTemplate("taskDef by name already exists" ).addConstraintViolation();
                }
            }

            return valid;
        }

        private boolean isTaskParamValid(Map<String, Object> inputParam, WorkflowDef workflowDef) {
            return true;
        }

//        //deep clone using json - POJO
//        private Map<String, Object> clone(Map<String, Object> inputTemplate) {
//            try {
//                byte[] bytes = objectMapper.writeValueAsBytes(inputTemplate);
//                return objectMapper.readValue(bytes, map);
//            } catch (IOException e) {
//                throw new RuntimeException("Unable to clone input params", e);
//            }
//        }
//
//        public Map<String, Object> getTaskInput(Map<String, Object> input, WorkflowDef workflowDef) {
//            Map<String, Object> inputParams;
//
//            if (input != null) {
//                inputParams = clone(input);
//            } else {
//                inputParams = new HashMap<>();
//            }
//
//
//            Map<String, Map<String, Object>> inputMap = new HashMap<>();
//
//            /*Map<String, Object> workflowParams = new HashMap<>();
//            workflowParams.put("input", workflow.getInput());
//            workflowParams.put("output", workflow.getOutput());
//            workflowParams.put("status", workflow.getStatus());
//            workflowParams.put("workflowId", workflow.getWorkflowId());
//            workflowParams.put("parentWorkflowId", workflow.getParentWorkflowId());
//            workflowParams.put("parentWorkflowTaskId", workflow.getParentWorkflowTaskId());
//            workflowParams.put("workflowType", workflow.getWorkflowName());
//            workflowParams.put("version", workflow.getWorkflowVersion());
//            workflowParams.put("correlationId", workflow.getCorrelationId());
//            workflowParams.put("reasonForIncompletion", workflow.getReasonForIncompletion());
//            workflowParams.put("schemaVersion", workflow.getSchemaVersion());*/
//
//            //inputMap.put("workflow", workflowParams);
//
//            //For new workflow being started the list of tasks will be empty
//            workflowDef.getTasks().stream()
//                    .map(WorkflowTask::getTaskReferenceName)
//                    .map(workflowDef::getTaskByRefName)
//                    .forEach(workflowTask  -> {
//                        Map<String, Object> taskParams = new HashMap<>();
//                        taskParams.put("input", workflowTask.getInputParameters());
//                        inputMap.put(workflowTask.getTaskReferenceName(), taskParams);
//                    });
//
//            Configuration option = Configuration.defaultConfiguration()
//                    .addOptions(Option.SUPPRESS_EXCEPTIONS);
//            DocumentContext documentContext = JsonPath.parse(inputMap, option);
//            return replace(inputParams, documentContext, taskId);
//        }


        private boolean verifyTaskInput(WorkflowDef workflow, Map<String, Object> inputParams) {
            final boolean[] valid = {true};

            Map<String, Object> input = new HashMap<>();
            if (inputParams == null) {
                return valid[0];
            }
            inputParams.entrySet().forEach(e -> {

                String paramName = e.getKey();
                String paramPath = "" + e.getValue();
                String[] paramPathComponents = paramPath.split("\\.");
                Preconditions.checkArgument(paramPathComponents.length == 3,
                        "Invalid input expression for " + paramName + ", paramPathComponents.size=" + paramPathComponents.length + ", expression=" + paramPath);

                String source = paramPathComponents[0];    //workflow, or task reference name
                String type = paramPathComponents[1];    //input/output
                String name = paramPathComponents[2];    //name of the parameter
                if ("workflow".equals(source)) {
                    //do nothing
                } else {
                    WorkflowTask task = workflow.getTaskByRefName(source);
                    if (task != null) {
                        if ((!("input".equals(type) || "output".equals(type)))) {
                            valid[0] = false;
                        }
                    }
                }
            });

            return valid[0];
        }
    }
}