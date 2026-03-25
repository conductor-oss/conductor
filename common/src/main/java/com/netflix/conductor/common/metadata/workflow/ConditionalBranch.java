package com.netflix.conductor.common.metadata.workflow;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;

/**
 * Represents a branch in a CONDITIONAL_FORK task. Each branch can have an optional condition.
 * Branches with no condition always run. Branches with a condition run only when the condition
 * evaluates to true.
 */
public class ConditionalBranch {

    /**
     * Optional condition. If null or empty, the branch always runs. If present, must contain
     * evaluatorType and expression.
     */
    private ConditionSpec condition;

    private List<@Valid WorkflowTask> tasks = new LinkedList<>();

    /** If true, when this branch fails the workflow terminates. Default false. */
    private boolean failWorkflowOnFailure = false;

    public ConditionSpec getCondition() {
        return condition;
    }

    public void setCondition(ConditionSpec condition) {
        this.condition = condition;
    }

    public List<WorkflowTask> getTasks() {
        return tasks != null ? tasks : Collections.emptyList();
    }

    public void setTasks(List<WorkflowTask> tasks) {
        this.tasks = tasks != null ? tasks : new LinkedList<>();
    }

    public boolean isFailWorkflowOnFailure() {
        return failWorkflowOnFailure;
    }

    public void setFailWorkflowOnFailure(boolean failWorkflowOnFailure) {
        this.failWorkflowOnFailure = failWorkflowOnFailure;
    }

    /** Condition specification for conditional branch evaluation. */
    public static class ConditionSpec {
        private String evaluatorType;
        private String expression;
        private Map<String, Object> input;
        private Map<String, Object> query;

        public String getEvaluatorType() {
            return evaluatorType;
        }

        public void setEvaluatorType(String evaluatorType) {
            this.evaluatorType = evaluatorType;
        }

        public String getExpression() {
            return expression;
        }

        public void setExpression(String expression) {
            this.expression = expression;
        }

        public Map<String, Object> getInput() {
            return input;
        }

        public void setInput(Map<String, Object> input) {
            this.input = input;
        }

        public Map<String, Object> getQuery() {
            return query;
        }

        public void setQuery(Map<String, Object> query) {
            this.query = query;
        }

        @SuppressWarnings("unchecked")
        public static ConditionSpec fromMap(Map<String, Object> map) {
            if (map == null || map.isEmpty()) {
                return null;
            }
            ConditionSpec spec = new ConditionSpec();
            Object et = map.get("evaluatorType");
            Object exp = map.get("expression");
            Object inputObj = map.get("input");
            Object queryObj = map.get("query");

            if (et != null) {
                spec.setEvaluatorType(et.toString());
            }
            if (exp != null) {
                spec.setExpression(exp.toString());
            }
            if (inputObj instanceof Map) {
                spec.setInput((Map<String, Object>) inputObj);
            }
            if (queryObj instanceof Map) {
                spec.setQuery((Map<String, Object>) queryObj);
            }

            return spec.getEvaluatorType() != null && spec.getExpression() != null ? spec : null;
        }
    }
}
