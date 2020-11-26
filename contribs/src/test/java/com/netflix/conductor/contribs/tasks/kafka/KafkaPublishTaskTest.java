/*
 * Copyright 2020 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.contribs.tasks.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.common.config.ObjectMapperConfiguration;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.serialization.IntegerSerializer;
import org.apache.kafka.common.serialization.LongSerializer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static org.junit.Assert.assertEquals;

@SuppressWarnings({"unchecked", "rawtypes"})
@ContextConfiguration(classes = {ObjectMapperConfiguration.class})
@RunWith(SpringRunner.class)
public class KafkaPublishTaskTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void missingRequest_Fail() {
        KafkaPublishTask kafkaPublishTask = new KafkaPublishTask(getKafkaProducerManager(), objectMapper);
        Task task = new Task();
        kafkaPublishTask.start(Mockito.mock(Workflow.class), task, Mockito.mock(WorkflowExecutor.class));
        assertEquals(Task.Status.FAILED, task.getStatus());
    }

    @Test
    public void missingValue_Fail() {

        Task task = new Task();
        KafkaPublishTask.Input input = new KafkaPublishTask.Input();
        input.setBootStrapServers("localhost:9092");
        input.setTopic("testTopic");

        task.getInputData().put(KafkaPublishTask.REQUEST_PARAMETER_NAME, input);

        KafkaPublishTask kPublishTask = new KafkaPublishTask(getKafkaProducerManager(), objectMapper);
        kPublishTask.start(Mockito.mock(Workflow.class), task, Mockito.mock(WorkflowExecutor.class));
        assertEquals(Task.Status.FAILED, task.getStatus());
    }

    @Test
    public void missingBootStrapServers_Fail() {

        Task task = new Task();
        KafkaPublishTask.Input input = new KafkaPublishTask.Input();

        Map<String, Object> value = new HashMap<>();
        input.setValue(value);
        input.setTopic("testTopic");

        task.getInputData().put(KafkaPublishTask.REQUEST_PARAMETER_NAME, input);

        KafkaPublishTask kPublishTask = new KafkaPublishTask(getKafkaProducerManager(), objectMapper);
        kPublishTask.start(Mockito.mock(Workflow.class), task, Mockito.mock(WorkflowExecutor.class));
        assertEquals(Task.Status.FAILED, task.getStatus());
    }


    @Test
    public void kafkaPublishExecutionException_Fail() throws ExecutionException, InterruptedException {

        Task task = getTask();

        KafkaProducerManager producerManager = Mockito.mock(KafkaProducerManager.class);
        KafkaPublishTask kafkaPublishTask = new KafkaPublishTask(producerManager, objectMapper);

        Producer producer = Mockito.mock(Producer.class);

        Mockito.when(producerManager.getProducer(Mockito.any())).thenReturn(producer);
        Future publishingFuture = Mockito.mock(Future.class);
        Mockito.when(producer.send(Mockito.any())).thenReturn(publishingFuture);

        ExecutionException executionException = Mockito.mock(ExecutionException.class);

        Mockito.when(executionException.getMessage()).thenReturn("Execution exception");
        Mockito.when(publishingFuture.get()).thenThrow(executionException);

        kafkaPublishTask.start(Mockito.mock(Workflow.class), task, Mockito.mock(WorkflowExecutor.class));
        assertEquals(Task.Status.FAILED, task.getStatus());
        assertEquals("Failed to invoke kafka task due to: Execution exception", task.getReasonForIncompletion());
    }


    @Test
    public void kafkaPublishUnknownException_Fail() {

        Task task = getTask();

        KafkaProducerManager producerManager = Mockito.mock(KafkaProducerManager.class);
        KafkaPublishTask kPublishTask = new KafkaPublishTask(producerManager, objectMapper);

        Producer producer = Mockito.mock(Producer.class);

        Mockito.when(producerManager.getProducer(Mockito.any())).thenReturn(producer);
        Mockito.when(producer.send(Mockito.any())).thenThrow(new RuntimeException("Unknown exception"));

        kPublishTask.start(Mockito.mock(Workflow.class), task, Mockito.mock(WorkflowExecutor.class));
        assertEquals(Task.Status.FAILED, task.getStatus());
        assertEquals("Failed to invoke kafka task due to: Unknown exception", task.getReasonForIncompletion());
    }

    @Test
    public void kafkaPublishSuccess_Completed() {

        Task task = getTask();

        KafkaProducerManager producerManager = Mockito.mock(KafkaProducerManager.class);
        KafkaPublishTask kPublishTask = new KafkaPublishTask(producerManager, objectMapper);

        Producer producer = Mockito.mock(Producer.class);

        Mockito.when(producerManager.getProducer(Mockito.any())).thenReturn(producer);
        Mockito.when(producer.send(Mockito.any())).thenReturn(Mockito.mock(Future.class));

        kPublishTask.start(Mockito.mock(Workflow.class), task, Mockito.mock(WorkflowExecutor.class));
        assertEquals(Task.Status.COMPLETED, task.getStatus());
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
        KafkaPublishTask kPublishTask = new KafkaPublishTask(getKafkaProducerManager(), objectMapper);
        KafkaPublishTask.Input input = new KafkaPublishTask.Input();
        input.setKeySerializer(IntegerSerializer.class.getCanonicalName());
        input.setKey(String.valueOf(Integer.MAX_VALUE));
        assertEquals(kPublishTask.getKey(input), Integer.MAX_VALUE);
    }

    @Test
    public void longSerializer_longObject() {
        KafkaPublishTask kPublishTask = new KafkaPublishTask(getKafkaProducerManager(), objectMapper);
        KafkaPublishTask.Input input = new KafkaPublishTask.Input();
        input.setKeySerializer(LongSerializer.class.getCanonicalName());
        input.setKey(String.valueOf(Long.MAX_VALUE));
        assertEquals(kPublishTask.getKey(input), Long.MAX_VALUE);
    }

    @Test
    public void noSerializer_StringObject() {
        KafkaPublishTask kPublishTask = new KafkaPublishTask(getKafkaProducerManager(), objectMapper);
        KafkaPublishTask.Input input = new KafkaPublishTask.Input();
        input.setKey("testStringKey");
        assertEquals(kPublishTask.getKey(input), "testStringKey");
    }

    private KafkaProducerManager getKafkaProducerManager() {
        return new KafkaProducerManager("100", "500", 120000, 10);
    }
}
