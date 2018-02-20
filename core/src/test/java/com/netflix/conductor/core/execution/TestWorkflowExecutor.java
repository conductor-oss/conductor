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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.Task.Status;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask.Type;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.execution.mapper.DecisionTaskMapper;
import com.netflix.conductor.core.execution.mapper.DynamicTaskMapper;
import com.netflix.conductor.core.execution.mapper.EventTaskMapper;
import com.netflix.conductor.core.execution.mapper.ForkJoinDynamicTaskMapper;
import com.netflix.conductor.core.execution.mapper.ForkJoinTaskMapper;
import com.netflix.conductor.core.execution.mapper.JoinTaskMapper;
import com.netflix.conductor.core.execution.mapper.SimpleTaskMapper;
import com.netflix.conductor.core.execution.mapper.SubWorkflowTaskMapper;
import com.netflix.conductor.core.execution.mapper.TaskMapper;
import com.netflix.conductor.core.execution.mapper.UserDefinedTaskMapper;
import com.netflix.conductor.core.execution.mapper.WaitTaskMapper;
import com.netflix.conductor.core.execution.tasks.Wait;
import com.netflix.conductor.core.execution.tasks.WorkflowSystemTask;
import com.netflix.conductor.core.utils.IDGenerator;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.dao.QueueDAO;
import org.junit.Test;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
		MetadataDAO metadataDAO = mock(MetadataDAO.class);
		ExecutionDAO edao = mock(ExecutionDAO.class);
		QueueDAO queue = mock(QueueDAO.class);
		ObjectMapper objectMapper = new ObjectMapper();
		ParametersUtils parametersUtils = new ParametersUtils();
		Map<String, TaskMapper> taskMappers = new HashMap<>();
		taskMappers.put("DECISION", new DecisionTaskMapper());
		taskMappers.put("DYNAMIC", new DynamicTaskMapper(parametersUtils, metadataDAO));
		taskMappers.put("FORK_JOIN", new ForkJoinTaskMapper());
		taskMappers.put("JOIN", new JoinTaskMapper());
		taskMappers.put("FORK_JOIN_DYNAMIC", new ForkJoinDynamicTaskMapper(parametersUtils, objectMapper));
		taskMappers.put("USER_DEFINED", new UserDefinedTaskMapper(parametersUtils, metadataDAO));
		taskMappers.put("SIMPLE", new SimpleTaskMapper(parametersUtils, metadataDAO));
		taskMappers.put("SUB_WORKFLOW", new SubWorkflowTaskMapper(parametersUtils, metadataDAO));
		taskMappers.put("EVENT", new EventTaskMapper(parametersUtils));
		taskMappers.put("WAIT", new WaitTaskMapper(parametersUtils));
		DeciderService deciderService = new DeciderService(metadataDAO, taskMappers);
		WorkflowExecutor executor = new WorkflowExecutor(deciderService, metadataDAO, edao, queue, config);
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

		Task task1 = new Task();
        task1.setTaskType(taskToSchedule.getType());
        task1.setTaskDefName(taskToSchedule.getName());
        task1.setReferenceTaskName(taskToSchedule.getTaskReferenceName());
        task1.setWorkflowInstanceId(workflow.getWorkflowId());
        task1.setCorrelationId(workflow.getCorrelationId());
        task1.setScheduledTime(System.currentTimeMillis());
        task1.setTaskId(IDGenerator.generate());
        task1.setInputData(new HashMap<>());
        task1.setStatus(Status.SCHEDULED);
        task1.setRetryCount(0);
        task1.setCallbackAfterSeconds(taskToSchedule.getStartDelay());
        task1.setWorkflowTask(taskToSchedule);



        Task task2 = new Task();
        task2.setTaskType(Wait.NAME);
        task2.setTaskDefName(taskToSchedule.getName());
        task2.setReferenceTaskName(taskToSchedule.getTaskReferenceName());
        task2.setWorkflowInstanceId(workflow.getWorkflowId());
        task2.setCorrelationId(workflow.getCorrelationId());
        task2.setScheduledTime(System.currentTimeMillis());
        task2.setEndTime(System.currentTimeMillis());
        task2.setInputData(new HashMap<>());
        task2.setTaskId(IDGenerator.generate());
        task2.setStatus(Status.IN_PROGRESS);
        task2.setWorkflowTask(taskToSchedule);

        Task task3 = new Task();
        task3.setTaskType(taskToSchedule2.getType());
        task3.setTaskDefName(taskToSchedule.getName());
        task3.setReferenceTaskName(taskToSchedule.getTaskReferenceName());
        task3.setWorkflowInstanceId(workflow.getWorkflowId());
        task3.setCorrelationId(workflow.getCorrelationId());
        task3.setScheduledTime(System.currentTimeMillis());
        task3.setTaskId(IDGenerator.generate());
        task3.setInputData(new HashMap<>());
        task3.setStatus(Status.SCHEDULED);
        task3.setRetryCount(0);
        task3.setCallbackAfterSeconds(taskToSchedule.getStartDelay());
        task3.setWorkflowTask(taskToSchedule);

		tasks.add(task1);
		tasks.add(task2);
		tasks.add(task3);


		when(edao.createTasks(tasks)).thenReturn(tasks);
		AtomicInteger startedTaskCount = new AtomicInteger(0);
		doAnswer(invocation -> {
            startedTaskCount.incrementAndGet();
            return null;
        }).when(edao)
                .updateTask(any());

		AtomicInteger queuedTaskCount = new AtomicInteger(0);
		doAnswer(invocation -> {
            String queueName = invocation.getArgumentAt(0, String.class);
            System.out.println(queueName);
            queuedTaskCount.incrementAndGet();
            return null;
        }).when(queue)
                .push(any(), any(), anyInt());

		boolean stateChanged = executor.scheduleTask(workflow, tasks);
		assertEquals(2, startedTaskCount.get());
		assertEquals(1, queuedTaskCount.get());
		assertTrue(stateChanged);
		assertFalse(httpTaskExecuted.get());
		assertTrue(http2TaskExecuted.get());
	}


}
