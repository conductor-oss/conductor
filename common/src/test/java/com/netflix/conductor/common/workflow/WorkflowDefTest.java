/**
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
/**
 * 
 */
package com.netflix.conductor.common.workflow;

import com.netflix.conductor.common.metadata.workflow.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import org.junit.Before;
import org.junit.Test;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Viren
 *
 */
public class WorkflowDefTest {

	@Before
	public void before(){
		System.setProperty("NETFLIX_STACK", "test");
		System.setProperty("NETFLIX_ENVIRONMENT", "test");
		System.setProperty("TEST_ENV", "test");
	}


	private WorkflowTask createTask(int c){
		WorkflowTask task = new WorkflowTask();
		task.setName("junit_task_" + c);
		task.setTaskReferenceName("t" + c);
		return task;
	}
	
	@Test
	public void test() {
		String COND_TASK_WF = "COND_TASK_WF";
		List<WorkflowTask> wfts = new ArrayList<WorkflowTask>(10);
		for(int i = 0; i < 10; i++){
			wfts.add(createTask(i));
		}

		WorkflowDef wf = new WorkflowDef();
		wf.setName(COND_TASK_WF);
		wf.setDescription(COND_TASK_WF);
		
		WorkflowTask subCaseTask = new WorkflowTask();
		subCaseTask.setType(TaskType.DECISION.name());
		subCaseTask.setCaseValueParam("case2");
		subCaseTask.setName("case2");
		subCaseTask.setTaskReferenceName("case2");
		Map<String, List<WorkflowTask>> dcx = new HashMap<>();
		dcx.put("sc1", wfts.subList(4, 5));
		dcx.put("sc2", wfts.subList(5, 7));
		subCaseTask.setDecisionCases(dcx);

		WorkflowTask caseTask = new WorkflowTask();
		caseTask.setType(TaskType.DECISION.name());
		caseTask.setCaseValueParam("case");
		caseTask.setName("case");
		caseTask.setTaskReferenceName("case");
		Map<String, List<WorkflowTask>> dc = new HashMap<>();
		dc.put("c1", Arrays.asList(wfts.get(0), subCaseTask, wfts.get(1)));
		dc.put("c2", Collections.singletonList(wfts.get(3)));
		caseTask.setDecisionCases(dc);
		
		WorkflowTask finalTask = new WorkflowTask();
		finalTask.setName("junit_task_1");
		finalTask.setTaskReferenceName("tf");
		
		wf.getTasks().add(caseTask);
		wf.getTasks().addAll(wfts.subList(8, 9));		
		
		WorkflowTask nxt = wf.getNextTask("case");
		assertEquals("t8", nxt.getTaskReferenceName());
		
		nxt = wf.getNextTask("t8");
		assertNull(nxt);
		
		nxt = wf.getNextTask("t0");
		assertEquals("case2", nxt.getTaskReferenceName());
		
		nxt = wf.getNextTask("case2");
		assertEquals("t1", nxt.getTaskReferenceName());
	}

	@Test
	public void testWorkflowDefConstraints() {
        WorkflowDef workflowDef = new WorkflowDef();//name is null
        workflowDef.setSchemaVersion(2);

        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        Validator validator = factory.getValidator();
        Set<ConstraintViolation<Object>> result = validator.validate(workflowDef);
        assertEquals(2, result.size());

        List<String> validationErrors = new ArrayList<>();
        result.forEach(e -> validationErrors.add(e.getMessage()));

        assertTrue(validationErrors.contains("WorkflowDef name cannot be null or empty"));
        assertTrue(validationErrors.contains("WorkflowTask list cannot be empty"));
        //assertTrue(validationErrors.contains("workflowDef schemaVersion: 1 should be >= 2"));
    }

	@Test
	public void testWorkflowDefConstraintsWithMultipleEnvVariable() {
		WorkflowDef workflowDef = new WorkflowDef();//name is null
		workflowDef.setSchemaVersion(2);
		workflowDef.setName("test_env");

		WorkflowTask workflowTask_1 = new WorkflowTask();
		workflowTask_1.setName("task_1");
		workflowTask_1.setTaskReferenceName("task_1");
		workflowTask_1.setType(TaskType.TASK_TYPE_SIMPLE);

		Map<String, Object> inputParam = new HashMap<>();
		inputParam.put("taskId", "${CPEWF_TASK_ID}");
		inputParam.put("entryPoint", "${NETFLIX_ENVIRONMENT} ${NETFLIX_STACK} ${CPEWF_TASK_ID} ${workflow.input.status}");

		workflowTask_1.setInputParameters(inputParam);

		WorkflowTask workflowTask_2 = new WorkflowTask();
		workflowTask_2.setName("task_2");
		workflowTask_2.setTaskReferenceName("task_2");
		workflowTask_2.setType(TaskType.TASK_TYPE_SIMPLE);

		Map<String, Object> inputParam2 = new HashMap<>();
		inputParam2.put("env", inputParam);

		workflowTask_2.setInputParameters(inputParam2);

		List<WorkflowTask> tasks = new ArrayList<>();
		tasks.add(workflowTask_1);
		tasks.add(workflowTask_2);

		workflowDef.setTasks(tasks);

		ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
		Validator validator = factory.getValidator();
		Set<ConstraintViolation<Object>> result = validator.validate(workflowDef);
		assertEquals(0, result.size());
	}

	@Test
	public void testWorkflowDefConstraintsSingleEnvVariable() {
		WorkflowDef workflowDef = new WorkflowDef();//name is null
		workflowDef.setSchemaVersion(2);
		workflowDef.setName("test_env");

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

		ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
		Validator validator = factory.getValidator();
		Set<ConstraintViolation<Object>> result = validator.validate(workflowDef);
		assertEquals(0, result.size());
	}

	@Test
	public void testWorkflowDefConstraintsDualEnvVariable() {
		WorkflowDef workflowDef = new WorkflowDef();//name is null
		workflowDef.setSchemaVersion(2);
		workflowDef.setName("test_env");

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

		ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
		Validator validator = factory.getValidator();
		Set<ConstraintViolation<Object>> result = validator.validate(workflowDef);
		assertEquals(0, result.size());
	}


	@Test
	public void testWorkflowDefConstraintsWithMapAsInputParam() {
		WorkflowDef workflowDef = new WorkflowDef();//name is null
		workflowDef.setSchemaVersion(2);
		workflowDef.setName("test_env");

		WorkflowTask workflowTask_1 = new WorkflowTask();
		workflowTask_1.setName("task_1");
		workflowTask_1.setTaskReferenceName("task_1");
		workflowTask_1.setType(TaskType.TASK_TYPE_SIMPLE);

		Map<String, Object> inputParam = new HashMap<>();
		inputParam.put("taskId", "${CPEWF_TASK_ID} ${NETFLIX_STACK}");
		Map<String, Object> envInputParam = new HashMap<>();
		envInputParam.put("packageId", "${workflow.input.packageId}");
		envInputParam.put("taskId", "${CPEWF_TASK_ID}");
		envInputParam.put("NETFLIX_STACK", "${NETFLIX_STACK}");
		envInputParam.put("NETFLIX_ENVIRONMENT", "${NETFLIX_ENVIRONMENT}");

		inputParam.put("env", envInputParam);

		workflowTask_1.setInputParameters(inputParam);

		List<WorkflowTask> tasks = new ArrayList<>();
		tasks.add(workflowTask_1);

		workflowDef.setTasks(tasks);

		ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
		Validator validator = factory.getValidator();
		Set<ConstraintViolation<Object>> result = validator.validate(workflowDef);
		assertEquals(0, result.size());
	}

	@Test
	public void testWorkflowTaskInputParamInvalid() {
		WorkflowDef workflowDef = new WorkflowDef();//name is null
		workflowDef.setSchemaVersion(2);
		workflowDef.setName("test_env");

		WorkflowTask workflowTask = new WorkflowTask();//name is null
		workflowTask.setName("t1");
		workflowTask.setWorkflowTaskType(TaskType.SIMPLE);
		workflowTask.setTaskReferenceName("t1");

		Map<String, Object> map = new HashMap<>();
		map.put("blabla", "${workflow.input.Space Value}");
		workflowTask.setInputParameters(map);

		workflowDef.getTasks().add(workflowTask);

		ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
		Validator validator = factory.getValidator();
		Set<ConstraintViolation<Object>> result = validator.validate(workflowDef);
		assertEquals(1, result.size());

		List<String> validationErrors = new ArrayList<>();
		result.forEach(e -> validationErrors.add(e.getMessage()));

		assertTrue(validationErrors.contains("key: blabla input parameter value: workflow.input.Space Value is not valid"));
	}


	@Test
	public void testWorkflowTaskInputParamValueInvalid() {
		WorkflowDef workflowDef = new WorkflowDef();//name is null
		workflowDef.setSchemaVersion(2);
		workflowDef.setName("test_env");

		WorkflowTask workflowTask = new WorkflowTask();//name is null

		workflowTask.setName("t1");
		workflowTask.setWorkflowTaskType(TaskType.SIMPLE);
		workflowTask.setTaskReferenceName("t1");

		Map<String, Object> map = new HashMap<>();
		map.put("blabla", "");
		workflowTask.setInputParameters(map);

		workflowDef.getTasks().add(workflowTask);

		ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
		Validator validator = factory.getValidator();
		Set<ConstraintViolation<Object>> result = validator.validate(workflowDef);
		assertEquals(1, result.size());

		List<String> validationErrors = new ArrayList<>();
		result.forEach(e -> validationErrors.add(e.getMessage()));

		assertTrue(validationErrors.contains("key: blabla input parameter value: is null or empty"));
	}

	@Test
	public void testWorkflowSchemaVersion1() {
		WorkflowDef workflowDef = new WorkflowDef();//name is null
		workflowDef.setSchemaVersion(3);
		workflowDef.setName("test_env");

		WorkflowTask workflowTask = new WorkflowTask();

		workflowTask.setName("t1");
		workflowTask.setWorkflowTaskType(TaskType.SIMPLE);
		workflowTask.setTaskReferenceName("t1");

		Map<String, Object> map = new HashMap<>();
		map.put("blabla", "");
		workflowTask.setInputParameters(map);

		workflowDef.getTasks().add(workflowTask);

		ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
		Validator validator = factory.getValidator();
		Set<ConstraintViolation<Object>> result = validator.validate(workflowDef);
		assertEquals(2, result.size());

		List<String> validationErrors = new ArrayList<>();
		result.forEach(e -> validationErrors.add(e.getMessage()));

		assertTrue(validationErrors.contains("workflowDef schemaVersion: 2 is only supported"));
	}
}
