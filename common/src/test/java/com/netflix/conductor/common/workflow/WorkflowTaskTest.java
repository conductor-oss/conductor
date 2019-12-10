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
package com.netflix.conductor.common.workflow;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.netflix.conductor.common.metadata.workflow.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import org.junit.Test;

/**
 * @author Viren
 *
 */
public class WorkflowTaskTest {

	@Test
	public void test() {
		WorkflowTask wt = new WorkflowTask();
		wt.setWorkflowTaskType(TaskType.DECISION);
		
		assertNotNull(wt.getType());
		assertEquals(TaskType.DECISION.name(), wt.getType());
	}
	
	@Test
	public void testOptional() {
		WorkflowTask task = new WorkflowTask();
		assertFalse(task.isOptional());
		
		task.setOptional(Boolean.FALSE);
		assertFalse(task.isOptional());
		
		task.setOptional(Boolean.TRUE);
		assertTrue(task.isOptional());
	}

	@Test
	public void testWorkflowTaskName() {
		WorkflowTask taskDef = new WorkflowTask();//name is null
		ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
		Validator validator = factory.getValidator();
		Set<ConstraintViolation<Object>> result = validator.validate(taskDef);
		assertEquals(2, result.size());

		List<String> validationErrors = new ArrayList<>();
		result.forEach(e -> validationErrors.add(e.getMessage()));

		assertTrue(validationErrors.contains("WorkflowTask name cannot be empty or null"));
		assertTrue(validationErrors.contains("WorkflowTask taskReferenceName name cannot be empty or null"));
	}
}
