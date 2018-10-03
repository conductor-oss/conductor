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
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * @author Viren
 *
 */
public class TestWorkflowDef {

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
}
