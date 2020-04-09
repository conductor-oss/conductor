package com.netflix.conductor.core.execution.tasks;

import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.service.MetadataService;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Arrays;

public class TestIsolatedTaskQueueProducer {

	@Test
	public void addTaskQueuesAddsElementToQueue() throws InterruptedException {

		SystemTaskWorkerCoordinator.taskNameWorkflowTaskMapping.put("HTTP", Mockito.mock(WorkflowSystemTask.class));
		MetadataService metadataService = Mockito.mock(MetadataService.class);
		IsolatedTaskQueueProducer isolatedTaskQueueProducer = new IsolatedTaskQueueProducer(metadataService, Mockito.mock(Configuration.class));
		TaskDef taskDef = new TaskDef();
		taskDef.setIsolationGroupId("isolated");
		Mockito.when(metadataService.getTaskDefs()).thenReturn(Arrays.asList(taskDef));
		isolatedTaskQueueProducer.addTaskQueues();

		Assert.assertFalse(SystemTaskWorkerCoordinator.queue.isEmpty());

	}


}
