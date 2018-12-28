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


import static org.junit.Assert.*;

import com.netflix.conductor.common.metadata.workflow.TaskType;
import org.junit.Test;

import com.netflix.conductor.common.metadata.workflow.WorkflowTask;

/**
 * @author Viren
 *
 */
public class TestWorkflowTask {

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
}
