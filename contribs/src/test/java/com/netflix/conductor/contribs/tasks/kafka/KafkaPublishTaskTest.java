/*
 * Copyright 2022 Netflix, Inc.
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

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.serialization.IntegerSerializer;
import org.apache.kafka.common.serialization.LongSerializer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import com.netflix.conductor.common.config.TestObjectMapperConfiguration;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings({"unchecked", "rawtypes"})
@ContextConfiguration(classes = {TestObjectMapperConfiguration.class})
@RunWith(SpringRunner.class)
public class KafkaPublishTaskTest {

    @Autowired private ObjectMapper objectMapper;

    @Test
    public void missingRequest_Fail() {
        KafkaPublishTask kafkaPublishTask =
                new KafkaPublishTask(getKafkaProducerManager(), objectMapper);
        TaskModel task = new TaskModel();
        kafkaPublishTask.start(mock(WorkflowModel.class), task, mock(WorkflowExecutor.class));
        assertEquals(TaskModel.Status.FAILED, task.getStatus());
    }

    @Test
    public void missingValue_Fail() {

        TaskModel task = new TaskModel();
        KafkaPublishTask.Input input = new KafkaPublishTask.Input();
        input.setBootStrapServers("localhost:9092");
        input.setTopic("testTopic");

        task.getInputData().put(KafkaPublishTask.REQUEST_PARAMETER_NAME, input);

        KafkaPublishTask kPublishTask =
                new KafkaPublishTask(getKafkaProducerManager(), objectMapper);
        kPublishTask.start(mock(WorkflowModel.class), task, mock(WorkflowExecutor.class));
        assertEquals(TaskModel.Status.FAILED, task.getStatus());
    }

    @Test
    public void missingBootStrapServers_Fail() {

        TaskModel task = new TaskModel();
        KafkaPublishTask.Input input = new KafkaPublishTask.Input();

        Map<String, Object> value = new HashMap<>();
        input.setValue(value);
        input.setTopic("testTopic");

        task.getInputData().put(KafkaPublishTask.REQUEST_PARAMETER_NAME, input);

        KafkaPublishTask kPublishTask =
                new KafkaPublishTask(getKafkaProducerManager(), objectMapper);
        kPublishTask.start(mock(WorkflowModel.class), task, mock(WorkflowExecutor.class));
        assertEquals(TaskModel.Status.FAILED, task.getStatus());
    }

    @Test
    public void kafkaPublishExecutionException_Fail()
            throws ExecutionException, InterruptedException {

        TaskModel task = getTask();

        KafkaProducerManager producerManager = mock(KafkaProducerManager.class);
        KafkaPublishTask kafkaPublishTask = new KafkaPublishTask(producerManager, objectMapper);

        Producer producer = mock(Producer.class);

        when(producerManager.getProducer(any())).thenReturn(producer);
        Future publishingFuture = mock(Future.class);
        when(producer.send(any())).thenReturn(publishingFuture);

        ExecutionException executionException = mock(ExecutionException.class);

        when(executionException.getMessage()).thenReturn("Execution exception");
        when(publishingFuture.get()).thenThrow(executionException);

        kafkaPublishTask.start(mock(WorkflowModel.class), task, mock(WorkflowExecutor.class));
        assertEquals(TaskModel.Status.FAILED, task.getStatus());
        assertEquals(
                "Failed to invoke kafka task due to: Execution exception",
                task.getReasonForIncompletion());
    }

    @Test
    public void kafkaPublishUnknownException_Fail() {

        TaskModel task = getTask();

        KafkaProducerManager producerManager = mock(KafkaProducerManager.class);
        KafkaPublishTask kPublishTask = new KafkaPublishTask(producerManager, objectMapper);

        Producer producer = mock(Producer.class);

        when(producerManager.getProducer(any())).thenReturn(producer);
        when(producer.send(any())).thenThrow(new RuntimeException("Unknown exception"));

        kPublishTask.start(mock(WorkflowModel.class), task, mock(WorkflowExecutor.class));
        assertEquals(TaskModel.Status.FAILED, task.getStatus());
        assertEquals(
                "Failed to invoke kafka task due to: Unknown exception",
                task.getReasonForIncompletion());
    }

    @Test
    public void kafkaPublishSuccess_Completed() {

        TaskModel task = getTask();

        KafkaProducerManager producerManager = mock(KafkaProducerManager.class);
        KafkaPublishTask kPublishTask = new KafkaPublishTask(producerManager, objectMapper);

        Producer producer = mock(Producer.class);

        when(producerManager.getProducer(any())).thenReturn(producer);
        when(producer.send(any())).thenReturn(mock(Future.class));

        kPublishTask.start(mock(WorkflowModel.class), task, mock(WorkflowExecutor.class));
        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
    }

    @Test
    public void kafkaPublishSuccess_AsyncComplete() {

        TaskModel task = getTask();
        task.getInputData().put("asyncComplete", true);

        KafkaProducerManager producerManager = mock(KafkaProducerManager.class);
        KafkaPublishTask kPublishTask = new KafkaPublishTask(producerManager, objectMapper);

        Producer producer = mock(Producer.class);

        when(producerManager.getProducer(any())).thenReturn(producer);
        when(producer.send(any())).thenReturn(mock(Future.class));

        kPublishTask.start(mock(WorkflowModel.class), task, mock(WorkflowExecutor.class));
        assertEquals(TaskModel.Status.IN_PROGRESS, task.getStatus());
    }

    private TaskModel getTask() {
        TaskModel task = new TaskModel();
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
        KafkaPublishTask kPublishTask =
                new KafkaPublishTask(getKafkaProducerManager(), objectMapper);
        KafkaPublishTask.Input input = new KafkaPublishTask.Input();
        input.setKeySerializer(IntegerSerializer.class.getCanonicalName());
        input.setKey(String.valueOf(Integer.MAX_VALUE));
        assertEquals(kPublishTask.getKey(input), Integer.MAX_VALUE);
    }

    @Test
    public void longSerializer_longObject() {
        KafkaPublishTask kPublishTask =
                new KafkaPublishTask(getKafkaProducerManager(), objectMapper);
        KafkaPublishTask.Input input = new KafkaPublishTask.Input();
        input.setKeySerializer(LongSerializer.class.getCanonicalName());
        input.setKey(String.valueOf(Long.MAX_VALUE));
        assertEquals(kPublishTask.getKey(input), Long.MAX_VALUE);
    }

    @Test
    public void noSerializer_StringObject() {
        KafkaPublishTask kPublishTask =
                new KafkaPublishTask(getKafkaProducerManager(), objectMapper);
        KafkaPublishTask.Input input = new KafkaPublishTask.Input();
        input.setKey("testStringKey");
        assertEquals(kPublishTask.getKey(input), "testStringKey");
    }

    private KafkaProducerManager getKafkaProducerManager() {
        return new KafkaProducerManager(
                Duration.ofMillis(100), Duration.ofMillis(500), 120000, Duration.ofMillis(10));
    }
}
