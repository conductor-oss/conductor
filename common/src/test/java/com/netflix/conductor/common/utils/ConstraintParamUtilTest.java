package com.netflix.conductor.common.utils;

import com.netflix.conductor.common.metadata.workflow.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class ConstraintParamUtilTest {

    @Before
    public void before(){
        System.setProperty("NETFLIX_STACK", "test");
        System.setProperty("NETFLIX_ENVIRONMENT", "test");
        System.setProperty("TEST_ENV", "test");
    }

    private WorkflowDef constructWorkflowDef() {
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setSchemaVersion(2);
        workflowDef.setName("test_env");
        return workflowDef;
    }

    @Test
    public void testExtractParamPathComponents() {
        WorkflowDef workflowDef = constructWorkflowDef();

        WorkflowTask workflowTask_1 = new WorkflowTask();
        workflowTask_1.setName("task_1");
        workflowTask_1.setTaskReferenceName("task_1");
        workflowTask_1.setType(TaskType.TASK_TYPE_SIMPLE);

        Map<String, Object> inputParam = new HashMap<>();
        inputParam.put("taskId", "${CPEWF_TASK_ID}");

        workflowTask_1.setInputParameters(inputParam);

        List<WorkflowTask> tasks = new ArrayList<>();
        tasks.add(workflowTask_1);

        workflowDef.setTasks(tasks);

        List<String> results = ConstraintParamUtil.validateInputParam(inputParam, "task_1", workflowDef);
        assertEquals(results.size(), 0);
    }

    @Test
    public void testExtractParamPathComponentsWithMissingEnvVariable() {
        WorkflowDef workflowDef = constructWorkflowDef();

        WorkflowTask workflowTask_1 = new WorkflowTask();
        workflowTask_1.setName("task_1");
        workflowTask_1.setTaskReferenceName("task_1");
        workflowTask_1.setType(TaskType.TASK_TYPE_SIMPLE);

        Map<String, Object> inputParam = new HashMap<>();
        inputParam.put("taskId", "${CPEWF_TASK_ID} ${NETFLIX_STACK}");

        workflowTask_1.setInputParameters(inputParam);

        List<WorkflowTask> tasks = new ArrayList<>();
        tasks.add(workflowTask_1);

        workflowDef.setTasks(tasks);

        List<String> results = ConstraintParamUtil.validateInputParam(inputParam, "task_1", workflowDef);
        assertEquals(results.size(), 0);
    }

    @Test
    public void testExtractParamPathComponentsWithValidEnvVariable() {
        WorkflowDef workflowDef = constructWorkflowDef();

        WorkflowTask workflowTask_1 = new WorkflowTask();
        workflowTask_1.setName("task_1");
        workflowTask_1.setTaskReferenceName("task_1");
        workflowTask_1.setType(TaskType.TASK_TYPE_SIMPLE);

        Map<String, Object> inputParam = new HashMap<>();
        inputParam.put("taskId", "${CPEWF_TASK_ID}  ${workflow.input.status}");

        workflowTask_1.setInputParameters(inputParam);

        List<WorkflowTask> tasks = new ArrayList<>();
        tasks.add(workflowTask_1);

        workflowDef.setTasks(tasks);

        List<String> results = ConstraintParamUtil.validateInputParam(inputParam,"task_1", workflowDef);
        assertEquals(results.size(), 0);
    }

    @Test
    public void testExtractParamPathComponentsWithValidMap() {
        WorkflowDef workflowDef = constructWorkflowDef();

        WorkflowTask workflowTask_1 = new WorkflowTask();
        workflowTask_1.setName("task_1");
        workflowTask_1.setTaskReferenceName("task_1");
        workflowTask_1.setType(TaskType.TASK_TYPE_SIMPLE);

        Map<String, Object> inputParam = new HashMap<>();
        inputParam.put("taskId", "${CPEWF_TASK_ID}  ${workflow.input.status}");
        Map<String, Object> envInputParam = new HashMap<>();
        envInputParam.put("packageId", "${workflow.input.packageId}");
        envInputParam.put("taskId", "${CPEWF_TASK_ID}");
        envInputParam.put("NETFLIX_STACK", "${NETFLIX_STACK}");
        envInputParam.put("NETFLIX_ENVIRONMENT", "${NETFLIX_ENVIRONMENT}");
        envInputParam.put("TEST_ENV", "${TEST_ENV}");

        inputParam.put("env", envInputParam);
        workflowTask_1.setInputParameters(inputParam);

        List<WorkflowTask> tasks = new ArrayList<>();
        tasks.add(workflowTask_1);

        workflowDef.setTasks(tasks);

        List<String> results = ConstraintParamUtil.validateInputParam(inputParam,"task_1", workflowDef);
        assertEquals(results.size(), 0);
    }

    @Test
    public void testExtractParamPathComponentsWithInvalidEnv() {
        WorkflowDef workflowDef = constructWorkflowDef();

        WorkflowTask workflowTask_1 = new WorkflowTask();
        workflowTask_1.setName("task_1");
        workflowTask_1.setTaskReferenceName("task_1");
        workflowTask_1.setType(TaskType.TASK_TYPE_SIMPLE);

        Map<String, Object> inputParam = new HashMap<>();
        inputParam.put("taskId", "${CPEWF_TASK_ID}  ${workflow.input.status}");
        Map<String, Object> envInputParam = new HashMap<>();
        envInputParam.put("packageId", "${workflow.input.packageId}");
        envInputParam.put("taskId", "${CPEWF_TASK_ID}");
        envInputParam.put("TEST_ENV1", "${TEST_ENV1}");

        inputParam.put("env", envInputParam);
        workflowTask_1.setInputParameters(inputParam);

        List<WorkflowTask> tasks = new ArrayList<>();
        tasks.add(workflowTask_1);

        workflowDef.setTasks(tasks);

        List<String> results = ConstraintParamUtil.validateInputParam(inputParam,"task_1", workflowDef);
        assertEquals(results.size(), 1);
    }

    @Test
    public void testExtractParamPathComponentsWithInputParamEmpty() {
        WorkflowDef workflowDef = constructWorkflowDef();

        WorkflowTask workflowTask_1 = new WorkflowTask();
        workflowTask_1.setName("task_1");
        workflowTask_1.setTaskReferenceName("task_1");
        workflowTask_1.setType(TaskType.TASK_TYPE_SIMPLE);

        Map<String, Object> inputParam = new HashMap<>();
        inputParam.put("taskId", "");
        workflowTask_1.setInputParameters(inputParam);

        List<WorkflowTask> tasks = new ArrayList<>();
        tasks.add(workflowTask_1);

        workflowDef.setTasks(tasks);

        List<String> results = ConstraintParamUtil.validateInputParam(inputParam,"task_1", workflowDef);
        assertEquals(results.size(), 0);
    }

    @Test
    public void testExtractParamPathComponentsWithListInputParamWithEmptyString() {
        WorkflowDef workflowDef = constructWorkflowDef();

        WorkflowTask workflowTask_1 = new WorkflowTask();
        workflowTask_1.setName("task_1");
        workflowTask_1.setTaskReferenceName("task_1");
        workflowTask_1.setType(TaskType.TASK_TYPE_SIMPLE);

        Map<String, Object> inputParam = new HashMap<>();
        inputParam.put("taskId", new String[] {""});
        workflowTask_1.setInputParameters(inputParam);

        List<WorkflowTask> tasks = new ArrayList<>();
        tasks.add(workflowTask_1);

        workflowDef.setTasks(tasks);

        List<String> results = ConstraintParamUtil.validateInputParam(inputParam,"task_1", workflowDef);
        assertEquals(results.size(), 0);
    }

    @Test
    public void testExtractParamPathComponentsWithInputFieldWithSpace() {
        WorkflowDef workflowDef = constructWorkflowDef();

        WorkflowTask workflowTask_1 = new WorkflowTask();
        workflowTask_1.setName("task_1");
        workflowTask_1.setTaskReferenceName("task_1");
        workflowTask_1.setType(TaskType.TASK_TYPE_SIMPLE);

        Map<String, Object> inputParam = new HashMap<>();
        inputParam.put("taskId", "${CPEWF_TASK_ID}  ${workflow.input.status sta}");
        workflowTask_1.setInputParameters(inputParam);

        List<WorkflowTask> tasks = new ArrayList<>();
        tasks.add(workflowTask_1);

        workflowDef.setTasks(tasks);

        List<String> results = ConstraintParamUtil.validateInputParam(inputParam,"task_1", workflowDef);
        assertEquals(results.size(), 1);
    }

    @Test
    public void testExtractParamPathComponentsWithPredefineEnums() {
        WorkflowDef workflowDef = constructWorkflowDef();

        WorkflowTask workflowTask_1 = new WorkflowTask();
        workflowTask_1.setName("task_1");
        workflowTask_1.setTaskReferenceName("task_1");
        workflowTask_1.setType(TaskType.TASK_TYPE_SIMPLE);

        Map<String, Object> inputParam = new HashMap<>();
        inputParam.put("NETFLIX_ENV", "${CPEWF_TASK_ID}");
        inputParam.put("entryPoint", "/tools/pdfwatermarker_mux.py ${NETFLIX_ENV} ${CPEWF_TASK_ID} alpha");
        workflowTask_1.setInputParameters(inputParam);

        List<WorkflowTask> tasks = new ArrayList<>();
        tasks.add(workflowTask_1);

        workflowDef.setTasks(tasks);

        List<String> results = ConstraintParamUtil.validateInputParam(inputParam,"task_1", workflowDef);
        assertEquals(results.size(), 0);
    }

    @Test
    public void testExtractParamPathComponentsWithEscapedChar() {
        WorkflowDef workflowDef = constructWorkflowDef();

        WorkflowTask workflowTask_1 = new WorkflowTask();
        workflowTask_1.setName("task_1");
        workflowTask_1.setTaskReferenceName("task_1");
        workflowTask_1.setType(TaskType.TASK_TYPE_SIMPLE);

        Map<String, Object> inputParam = new HashMap<>();
        inputParam.put("taskId", "$${expression with spaces}");
        workflowTask_1.setInputParameters(inputParam);

        List<WorkflowTask> tasks = new ArrayList<>();
        tasks.add(workflowTask_1);

        workflowDef.setTasks(tasks);

        List<String> results = ConstraintParamUtil.validateInputParam(inputParam,"task_1", workflowDef);
        assertEquals(results.size(), 0);
    }
}