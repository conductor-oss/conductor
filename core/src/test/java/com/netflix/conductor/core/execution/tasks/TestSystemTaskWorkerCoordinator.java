package com.netflix.conductor.core.execution.tasks;

import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.config.SystemPropertiesConfiguration;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.dao.QueueDAO;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public class TestSystemTaskWorkerCoordinator {

	public static final String TEST_QUEUE = "test";
	public static final String ISOLATION_CONSTANT = "-iso";

	@Test
	public void testPollAndExecuteForIsolatedQueues() {

		createTaskMapping();

		Configuration configuration = Mockito.mock(Configuration.class);
		QueueDAO queueDao = Mockito.mock(QueueDAO.class);
		Mockito.when(configuration.getIntProperty(Mockito.anyString(), Mockito.anyInt())).thenReturn(10);
		Mockito.when(queueDao.pop(Mockito.anyString(), Mockito.anyInt(), Mockito.anyInt())).thenReturn(Arrays.asList("taskId"));
		WorkflowExecutor wfE = Mockito.mock(WorkflowExecutor.class);
		SystemTaskWorkerCoordinator systemTaskWorkerCoordinator = new SystemTaskWorkerCoordinator(queueDao, wfE, configuration);

		systemTaskWorkerCoordinator.pollAndExecute(TEST_QUEUE + ISOLATION_CONSTANT);
		shutDownExecutors(systemTaskWorkerCoordinator);

		Mockito.verify(wfE, Mockito.times(1)).executeSystemTask(Mockito.any(), Mockito.anyString(), Mockito.anyInt());

	}

	@Test
	public void testPollAndExecuteForTaskQueues() {

		createTaskMapping();

		Configuration configuration = Mockito.mock(Configuration.class);
		QueueDAO queueDao = Mockito.mock(QueueDAO.class);
		Mockito.when(configuration.getIntProperty(Mockito.any(), Mockito.anyInt())).thenReturn(10);
		Mockito.when(queueDao.pop(Mockito.any(), Mockito.anyInt(), Mockito.anyInt())).thenReturn(Arrays.asList("taskId"));
		WorkflowExecutor wfE = Mockito.mock(WorkflowExecutor.class);
		SystemTaskWorkerCoordinator systemTaskWorkerCoordinator = new SystemTaskWorkerCoordinator(queueDao, wfE, configuration);

		systemTaskWorkerCoordinator.pollAndExecute(TEST_QUEUE);
		shutDownExecutors(systemTaskWorkerCoordinator);

		Mockito.verify(wfE, Mockito.times(1)).executeSystemTask(Mockito.any(), Mockito.anyString(), Mockito.anyInt());

	}

	private void shutDownExecutors(SystemTaskWorkerCoordinator systemTaskWorkerCoordinator) {

		systemTaskWorkerCoordinator.defaultExecutionConfig.service.shutdown();

		try {
			systemTaskWorkerCoordinator.defaultExecutionConfig.service.awaitTermination(10, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		systemTaskWorkerCoordinator.queueExecutionConfigMap.values().stream().forEach(e -> {
			e.service.shutdown();
			try {
				e.service.awaitTermination(10, TimeUnit.MILLISECONDS);
			} catch (InterruptedException ie) {

			}
		});

	}

	@Test
	public void isSystemTask() {

		createTaskMapping();
		Assert.assertTrue(SystemTaskWorkerCoordinator.isSystemTask(TEST_QUEUE + ISOLATION_CONSTANT));

	}

	@Test
	public void isSystemTaskNotPresent() {

		createTaskMapping();
		Assert.assertFalse(SystemTaskWorkerCoordinator.isSystemTask(null));

	}

	private void createTaskMapping() {

		WorkflowSystemTask mockWfTask = Mockito.mock(WorkflowSystemTask.class);
		Mockito.when(mockWfTask.getName()).thenReturn(TEST_QUEUE);
		Mockito.when(mockWfTask.isAsync()).thenReturn(true);
		SystemTaskWorkerCoordinator.taskNameWorkFlowTaskMapping.put(TEST_QUEUE, mockWfTask);

	}

	@Test
	public void testGetExecutionConfigForSystemTask() {

		Configuration configuration = Mockito.mock(Configuration.class);
		Mockito.when(configuration.getIntProperty(Mockito.anyString(), Mockito.anyInt())).thenReturn(1);
		SystemTaskWorkerCoordinator systemTaskWorkerCoordinator = new SystemTaskWorkerCoordinator(Mockito.mock(QueueDAO.class), Mockito.mock(WorkflowExecutor.class), configuration);
		Assert.assertEquals(systemTaskWorkerCoordinator.getExecutionConfig("").workerQueue.remainingCapacity(), 1);

	}

	@Test
	public void testGetExecutionConfigForIsolatedSystemTask() {

		Configuration configuration = new SystemPropertiesConfiguration();
		SystemTaskWorkerCoordinator systemTaskWorkerCoordinator = new SystemTaskWorkerCoordinator(Mockito.mock(QueueDAO.class), Mockito.mock(WorkflowExecutor.class), configuration);
		Assert.assertEquals(systemTaskWorkerCoordinator.getExecutionConfig("test-iso").workerQueue.remainingCapacity(), 100);

	}


	@Test
	public void testIsFromCoordinatorDomain() {
		System.setProperty("workflow.system.task.worker.domain","domain");
		Configuration configuration = new SystemPropertiesConfiguration();
		SystemTaskWorkerCoordinator systemTaskWorkerCoordinator = new SystemTaskWorkerCoordinator(Mockito.mock(QueueDAO.class), Mockito.mock(WorkflowExecutor.class), configuration);
		Assert.assertEquals(systemTaskWorkerCoordinator.isFromCoordinatorExecutionNameSpace("domain:testTaskType"), true);

	}
}
