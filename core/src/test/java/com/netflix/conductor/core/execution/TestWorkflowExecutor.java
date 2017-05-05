/**
 * Copyright 2017 Netflix, Inc.
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
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.Task.Status;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask.Type;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.execution.tasks.Wait;
import com.netflix.conductor.core.execution.tasks.WorkflowSystemTask;
import com.netflix.conductor.core.utils.IDGenerator;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.dao.QueueDAO;

/**
 * @author Viren
 *
 */
public class TestWorkflowExecutor {

	@Test
	public void test() throws Exception {
		
		AtomicBoolean httpTaskExecuted = new AtomicBoolean(false);
		AtomicBoolean http2TaskExecuted = new AtomicBoolean(false);
	
		new Wait();
		new WorkflowSystemTask("HTTP") {
			@Override
			public boolean isAsync() {
				return true;
			}
			
			@Override
			public void start(Workflow workflow, Task task, WorkflowExecutor executor) throws Exception {
				httpTaskExecuted.set(true);
				task.setStatus(Status.COMPLETED);
				super.start(workflow, task, executor);
			}
			
		};
		
		new WorkflowSystemTask("HTTP2") {
			
			@Override
			public void start(Workflow workflow, Task task, WorkflowExecutor executor) throws Exception {
				http2TaskExecuted.set(true);
				task.setStatus(Status.COMPLETED);
				super.start(workflow, task, executor);
			}
			
		};
		
		Workflow workflow = new Workflow();
		workflow.setWorkflowId("1");
		
		TestConfiguration config = new TestConfiguration();
		MetadataDAO metadata = mock(MetadataDAO.class);
		ExecutionDAO edao = mock(ExecutionDAO.class);
		QueueDAO queue = mock(QueueDAO.class);
		ObjectMapper om = new ObjectMapper();
		
		WorkflowExecutor executor = new WorkflowExecutor(metadata, edao, queue, om, config);
		List<Task> tasks = new LinkedList<>();
		
		WorkflowTask taskToSchedule = new WorkflowTask();
		taskToSchedule.setWorkflowTaskType(Type.USER_DEFINED);
		taskToSchedule.setType("HTTP");
		
		WorkflowTask taskToSchedule2 = new WorkflowTask();
		taskToSchedule2.setWorkflowTaskType(Type.USER_DEFINED);
		taskToSchedule2.setType("HTTP2");
		
		WorkflowTask wait = new WorkflowTask();
		wait.setWorkflowTaskType(Type.WAIT);
		wait.setType("WAIT");
		wait.setTaskReferenceName("wait");
		
		Task task1 = SystemTask.userDefined(workflow, IDGenerator.generate(), taskToSchedule, new HashMap<>(), null, 0);
		Task task2 = SystemTask.waitTask(workflow, IDGenerator.generate(), taskToSchedule, new HashMap<>());
		Task task3 = SystemTask.userDefined(workflow, IDGenerator.generate(), taskToSchedule2, new HashMap<>(), null, 0);
		
		tasks.add(task1);
		tasks.add(task2);
		tasks.add(task3);
		
		
		when(edao.createTasks(tasks)).thenReturn(tasks);
		AtomicInteger startedTaskCount = new AtomicInteger(0);
		doAnswer(new Answer<Void>() {

			@Override
			public Void answer(InvocationOnMock invocation) throws Throwable {
				startedTaskCount.incrementAndGet();
				return null;
			}
		}).when(edao).updateTask(any());

		AtomicInteger queuedTaskCount = new AtomicInteger(0);		
		doAnswer(new Answer<Void>() {

			@Override
			public Void answer(InvocationOnMock invocation) throws Throwable {
				String queueName = invocation.getArgumentAt(0, String.class);
				System.out.println(queueName);
				queuedTaskCount.incrementAndGet();
				return null;
			}
		}).when(queue).push(any(), any(), anyInt());

		boolean stateChanged = executor.scheduleTask(workflow, tasks);
		assertEquals(2, startedTaskCount.get());
		assertEquals(1, queuedTaskCount.get());
		assertTrue(stateChanged);
		assertFalse(httpTaskExecuted.get());
		assertTrue(http2TaskExecuted.get());
	}
	
	
}
