package com.netflix.conductor.contribs.kafka;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.utils.JsonMapperProvider;
import com.netflix.conductor.core.config.SystemPropertiesConfiguration;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.serialization.IntegerSerializer;
import org.apache.kafka.common.serialization.LongSerializer;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class TestKafkaPublishTask {

	private static ObjectMapper objectMapper = new JsonMapperProvider().get();

	@Test
	public void missingRequest_Fail() {
		KafkaPublishTask kPublishTask = new KafkaPublishTask(new SystemPropertiesConfiguration(), new KafkaProducerManager(new SystemPropertiesConfiguration()), objectMapper);
		Task task = new Task();
		kPublishTask.start(Mockito.mock(Workflow.class), task, Mockito.mock(WorkflowExecutor.class));
		Assert.assertEquals(Task.Status.FAILED, task.getStatus());
	}

	@Test
	public void missingValue_Fail() {

		Task task = new Task();
		KafkaPublishTask.Input input = new KafkaPublishTask.Input();
		input.setBootStrapServers("localhost:9092");
		input.setTopic("testTopic");

		task.getInputData().put(KafkaPublishTask.REQUEST_PARAMETER_NAME, input);

		KafkaPublishTask kPublishTask = new KafkaPublishTask(new SystemPropertiesConfiguration(), new KafkaProducerManager(new SystemPropertiesConfiguration()), objectMapper);
		kPublishTask.start(Mockito.mock(Workflow.class), task, Mockito.mock(WorkflowExecutor.class));
		Assert.assertEquals(Task.Status.FAILED, task.getStatus());
	}

	@Test
	public void missingBootStrapServers_Fail() {

		Task task = new Task();
		KafkaPublishTask.Input input = new KafkaPublishTask.Input();


		Map<String, Object> value = new HashMap<>();
		input.setValue(value);
		input.setTopic("testTopic");

		task.getInputData().put(KafkaPublishTask.REQUEST_PARAMETER_NAME, input);

		KafkaPublishTask kPublishTask = new KafkaPublishTask(new SystemPropertiesConfiguration(), new KafkaProducerManager(new SystemPropertiesConfiguration()), objectMapper);
		kPublishTask.start(Mockito.mock(Workflow.class), task, Mockito.mock(WorkflowExecutor.class));
		Assert.assertEquals(Task.Status.FAILED, task.getStatus());
	}


	@Test
	public void kafkaPublishExecutionException_Fail() throws ExecutionException, InterruptedException {

		Task task = getTask();

		KafkaProducerManager producerManager = Mockito.mock(KafkaProducerManager.class);
		KafkaPublishTask kPublishTask = new KafkaPublishTask(new SystemPropertiesConfiguration(), producerManager, objectMapper);

		Producer producer = Mockito.mock(Producer.class);

		Mockito.when(producerManager.getProducer(Mockito.any())).thenReturn(producer);
		Future publishingFuture = Mockito.mock(Future.class);
		Mockito.when(producer.send(Mockito.any())).thenReturn(publishingFuture);

		ExecutionException executionException = Mockito.mock(ExecutionException.class);


		Mockito.when(executionException.getMessage()).thenReturn("Execution exception");
		Mockito.when(publishingFuture.get()).thenThrow(executionException);

		kPublishTask.start(Mockito.mock(Workflow.class), task, Mockito.mock(WorkflowExecutor.class));
		Assert.assertEquals(Task.Status.FAILED, task.getStatus());
		Assert.assertEquals("Failed to invoke kafka task due to: Execution exception", task.getReasonForIncompletion());
	}


	@Test
	public void kafkaPublishUnknownException_Fail() throws ExecutionException, InterruptedException {

		Task task = getTask();

		KafkaProducerManager producerManager = Mockito.mock(KafkaProducerManager.class);
		KafkaPublishTask kPublishTask = new KafkaPublishTask(new SystemPropertiesConfiguration(), producerManager, objectMapper);

		Producer producer = Mockito.mock(Producer.class);

		Mockito.when(producerManager.getProducer(Mockito.any())).thenReturn(producer);
		Mockito.when(producer.send(Mockito.any())).thenThrow(new RuntimeException("Unknown exception"));


		kPublishTask.start(Mockito.mock(Workflow.class), task, Mockito.mock(WorkflowExecutor.class));
		Assert.assertEquals(Task.Status.FAILED, task.getStatus());
		Assert.assertEquals("Failed to invoke kafka task due to: Unknown exception", task.getReasonForIncompletion());
	}

	@Test
	public void kafkaPublishSuccess_Completed() throws ExecutionException, InterruptedException {

		Task task = getTask();

		KafkaProducerManager producerManager = Mockito.mock(KafkaProducerManager.class);
		KafkaPublishTask kPublishTask = new KafkaPublishTask(new SystemPropertiesConfiguration(), producerManager, objectMapper);

		Producer producer = Mockito.mock(Producer.class);

		Mockito.when(producerManager.getProducer(Mockito.any())).thenReturn(producer);
		Mockito.when(producer.send(Mockito.any())).thenReturn(Mockito.mock(Future.class));


		kPublishTask.start(Mockito.mock(Workflow.class), task, Mockito.mock(WorkflowExecutor.class));
		Assert.assertEquals(Task.Status.COMPLETED, task.getStatus());
	}

	private Task getTask() {
		Task task = new Task();
		KafkaPublishTask.Input input = new KafkaPublishTask.Input();
		input.setBootStrapServers("localhost:9092");

		Map<String, Object> value = new HashMap<>();

		value.put("input_key1", "value1");
		value.put("input_key2", 45.3d);

		input.setValue(value);
		input.setTopic("testTopic");
		task.getInputData().put(KafkaPublishTask.REQUEST_PARAMETER_NAME, input);
		return task;
	}

	@Test
	public void integerSerializer_integerObject() {
		KafkaPublishTask kPublishTask = new KafkaPublishTask(new SystemPropertiesConfiguration(), new KafkaProducerManager(new SystemPropertiesConfiguration()), objectMapper);
		KafkaPublishTask.Input input = new KafkaPublishTask.Input();
		input.setKeySerializer(IntegerSerializer.class.getCanonicalName());
		input.setKey(String.valueOf(Integer.MAX_VALUE));
		Assert.assertEquals(kPublishTask.getKey(input), new Integer(Integer.MAX_VALUE));
	}

	@Test
	public void longSerializer_longObject() {
		KafkaPublishTask kPublishTask = new KafkaPublishTask(new SystemPropertiesConfiguration(), new KafkaProducerManager(new SystemPropertiesConfiguration()), objectMapper);
		KafkaPublishTask.Input input = new KafkaPublishTask.Input();
		input.setKeySerializer(LongSerializer.class.getCanonicalName());
		input.setKey(String.valueOf(Long.MAX_VALUE));
		Assert.assertEquals(kPublishTask.getKey(input), new Long(Long.MAX_VALUE));
	}

	@Test
	public void noSerializer_StringObject() {
		KafkaPublishTask kPublishTask = new KafkaPublishTask(new SystemPropertiesConfiguration(), new KafkaProducerManager(new SystemPropertiesConfiguration()), objectMapper);
		KafkaPublishTask.Input input = new KafkaPublishTask.Input();
		input.setKey("testStringKey");
		Assert.assertEquals(kPublishTask.getKey(input), "testStringKey");
	}

}
