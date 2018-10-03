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
import static org.junit.Assert.assertNotNull;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.metadata.workflow.TaskType;

/**
 * @author Viren
 *
 */
public class TestWorkflowDef {

	@Test
	public void test(){
		WorkflowDef def = new WorkflowDef();
		def.setName("Test Workflow");
		def.setVersion(1);
		def.setSchemaVersion(1);
		def.getTasks().add(create("simple_task_1"));
		def.getTasks().add(create("simple_task_2"));
		
		WorkflowTask task3 = create("decision_task_1");
		def.getTasks().add(task3);
		task3.setType(TaskType.DECISION.name());
		task3.getDecisionCases().put("Case1", Arrays.asList(create("case_1_task_1"), create("case_1_task_2")));
		task3.getDecisionCases().put("Case2", Arrays.asList(create("case_2_task_1"), create("case_2_task_2")));
		task3.getDecisionCases().put("Case3", Arrays.asList(deciderTask("decision_task_2", toMap("Case31", "case31_task_1", "case_31_task_2"), Arrays.asList("case3_def_task"))));
		def.getTasks().add(create("simple_task_3"));
		
		//Assertions
		
		WorkflowTask next = def.getNextTask("simple_task_1");
		assertNotNull(next);
		assertEquals("simple_task_2", next.getTaskReferenceName());
		
		next = def.getNextTask("simple_task_2");
		assertNotNull(next);
		assertEquals(task3.getTaskReferenceName(), next.getTaskReferenceName());
		
		next = def.getNextTask("decision_task_1");
		assertNotNull(next);
		assertEquals("simple_task_3", next.getTaskReferenceName());
		
		
		next = def.getNextTask("case_1_task_1");
		assertNotNull(next);
		assertEquals("case_1_task_2", next.getTaskReferenceName());
		
		next = def.getNextTask("case_1_task_2");
		assertNotNull(next);
		assertEquals("simple_task_3", next.getTaskReferenceName());
		
		next = def.getNextTask("case3_def_task");
		assertNotNull(next);
		assertEquals("simple_task_3", next.getTaskReferenceName());
		
		next = def.getNextTask("case31_task_1");
		assertNotNull(next);
		assertEquals("case_31_task_2", next.getTaskReferenceName());
	}
	
	private WorkflowTask create(String name){
		WorkflowTask task = new WorkflowTask();
		task.setName(name);
		task.setTaskReferenceName(name);
		return task;
		
	}
	
	private WorkflowTask deciderTask(String name, Map<String, List<String>> decisions, List<String> defaultTasks){
		WorkflowTask task = create(name);
		task.setType(TaskType.DECISION.name());
		decisions.entrySet().forEach(e -> {
			List<WorkflowTask> tasks = new LinkedList<>();			
			e.getValue().forEach(taskName -> tasks.add(create(taskName)));
			task.getDecisionCases().put(e.getKey(), tasks);
		});
		List<WorkflowTask> tasks = new LinkedList<>();
		defaultTasks.forEach(defaultTask -> tasks.add(create(defaultTask)));
		task.setDefaultCase(tasks);
		return task;
	}
	
	private Map<String, List<String>> toMap(String key, String...values){
		Map<String, List<String>> map = new HashMap<>();
		List<String> vals = Arrays.asList(values);
		map.put(key, vals);
		return map;
	}
}
