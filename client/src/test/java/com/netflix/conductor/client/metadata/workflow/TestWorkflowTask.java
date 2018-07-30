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
package com.netflix.conductor.client.metadata.workflow;

import static org.junit.Assert.*;

import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask.Type;

/**
 * 
 * @author Viren
 *
 */
public class TestWorkflowTask {

	@Test
	public void test() throws Exception {
		ObjectMapper om = new ObjectMapper();
		WorkflowTask task = new WorkflowTask();
		task.setType("Hello");
		task.setName("name");
		
		String json = om.writeValueAsString(task);

		WorkflowTask read = om.readValue(json, WorkflowTask.class);
		assertNotNull(read);
		assertEquals(task.getName(), read.getName());
		assertEquals(task.getType(), read.getType());
		
		task = new WorkflowTask();
		task.setWorkflowTaskType(Type.SUB_WORKFLOW);
		task.setName("name");
		
		json = om.writeValueAsString(task);

		read = om.readValue(json, WorkflowTask.class);
		assertNotNull(read);
		assertEquals(task.getName(), read.getName());
		assertEquals(task.getType(), read.getType());
		assertEquals(Type.SUB_WORKFLOW.name(), read.getType());
	}

}
