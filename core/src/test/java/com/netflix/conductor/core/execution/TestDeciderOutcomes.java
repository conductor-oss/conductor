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
package com.netflix.conductor.core.execution;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.InputStream;

import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.common.metadata.tasks.Task.Status;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.execution.DeciderService.DeciderOutcome;
import com.netflix.conductor.dao.MetadataDAO;

/**
 * @author Viren
 *
 */
public class TestDeciderOutcomes {

	private DeciderService ds;
	
	private ObjectMapper om = new ObjectMapper();
	
	@Before
	public void init() throws Exception {
		
		MetadataDAO metadata = mock(MetadataDAO.class);
		TaskDef td = new TaskDef();
		when(metadata.getTaskDef(any())).thenReturn(td);
		this.ds = new DeciderService(metadata, om);
	}
	
	@Test
	public void testWorkflowWithNoTasks() throws Exception {
		InputStream stream = TestDeciderOutcomes.class.getResourceAsStream("/conditional_flow.json");
		WorkflowDef def = om.readValue(stream, WorkflowDef.class);
		assertNotNull(def);
		
		Workflow workflow = new Workflow();
		workflow.setWorkflowType(def.getName());
		workflow.setStartTime(0);
		workflow.getInput().put("param1", "nested");
		workflow.getInput().put("param2", "one");
		
		DeciderOutcome outcome = ds.decide(workflow, def);
		assertNotNull(outcome);
		assertFalse(outcome.isComplete);
		assertTrue(outcome.tasksToBeUpdated.isEmpty());
		assertEquals(3, outcome.tasksToBeScheduled.size());
		System.out.println(outcome.tasksToBeScheduled);
		
		outcome.tasksToBeScheduled.forEach(t -> t.setStatus(Status.COMPLETED));
		workflow.getTasks().addAll(outcome.tasksToBeScheduled);
		outcome = ds.decide(workflow, def);
		assertFalse(outcome.isComplete);
		assertEquals(outcome.tasksToBeUpdated.toString(), 3, outcome.tasksToBeUpdated.size());
		assertEquals(1, outcome.tasksToBeScheduled.size());
		assertEquals("junit_task_3", outcome.tasksToBeScheduled.get(0).getTaskDefName());
		System.out.println(outcome.tasksToBeScheduled);
	}
	
	
	
	
}
