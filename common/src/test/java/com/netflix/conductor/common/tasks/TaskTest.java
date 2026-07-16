/*
 * Copyright 2020 Conductor Authors.
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
package com.netflix.conductor.common.tasks;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Test;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.tasks.ExecutionMetadata;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.Task.Status;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Any;

import static org.junit.Assert.*;

public class TaskTest {

    private final ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();

    @Test
    public void test() {

        Task task = new Task();
        task.setStatus(Status.FAILED);
        assertEquals(Status.FAILED, task.getStatus());

        Set<String> resultStatues =
                Arrays.stream(TaskResult.Status.values())
                        .map(Enum::name)
                        .collect(Collectors.toSet());

        for (Status status : Status.values()) {
            if (resultStatues.contains(status.name())) {
                TaskResult.Status trStatus = TaskResult.Status.valueOf(status.name());
                assertEquals(status.name(), trStatus.name());

                task = new Task();
                task.setStatus(status);
                assertEquals(status, task.getStatus());
            }
        }
    }

    @Test
    public void testTaskDefinitionIfAvailable() {
        Task task = new Task();
        task.setStatus(Status.FAILED);
        assertEquals(Status.FAILED, task.getStatus());

        assertNull(task.getWorkflowTask());
        assertFalse(task.getTaskDefinition().isPresent());

        WorkflowTask workflowTask = new WorkflowTask();
        TaskDef taskDefinition = new TaskDef();
        workflowTask.setTaskDefinition(taskDefinition);
        task.setWorkflowTask(workflowTask);

        assertTrue(task.getTaskDefinition().isPresent());
        assertEquals(taskDefinition, task.getTaskDefinition().get());
    }

    @Test
    public void testTaskQueueWaitTime() {
        Task task = new Task();

        long currentTimeMillis = System.currentTimeMillis();
        task.setScheduledTime(currentTimeMillis - 30_000); // 30 seconds ago
        task.setStartTime(currentTimeMillis - 25_000);

        long queueWaitTime = task.getQueueWaitTime();
        assertEquals(5000L, queueWaitTime);

        task.setUpdateTime(currentTimeMillis - 20_000);
        task.setCallbackAfterSeconds(10);
        queueWaitTime = task.getQueueWaitTime();
        assertTrue(queueWaitTime > 0);
    }

    @Test
    public void testDeepCopyTask() {
        final Task task = new Task();
        // In order to avoid forgetting putting inside the copy method the newly added fields check
        // the number of declared fields.
        // NOTE: `runtimeMetadata` (wire-only resolved secret values, injected at poll time) is
        // intentionally NOT propagated by copy()/deepCopy() - see
        // testRuntimeMetadataExcludedFromCopy.
        final int expectedTaskFieldsNumber = 44;
        final int declaredFieldsNumber = task.getClass().getDeclaredFields().length;

        final ExecutionMetadata executionMetadata = new ExecutionMetadata();
        executionMetadata.setServerSendTime(1000L);
        executionMetadata.setClientReceiveTime(2000L);
        executionMetadata.setExecutionStartTime(3000L);
        executionMetadata.setExecutionEndTime(4000L);
        executionMetadata.setClientSendTime(5000L);
        executionMetadata.setPollNetworkLatency(6000L);
        executionMetadata.setUpdateNetworkLatency(7000L);
        executionMetadata.setAdditionalContextMap(new HashMap<>());

        assertEquals(expectedTaskFieldsNumber, declaredFieldsNumber);

        task.setCallbackAfterSeconds(111L);
        task.setCallbackFromWorker(false);
        task.setCorrelationId("correlation_id");
        task.setInputData(new HashMap<>());
        task.setOutputData(new HashMap<>());
        task.setReferenceTaskName("ref_task_name");
        task.setStartDelayInSeconds(1);
        task.setTaskDefName("task_def_name");
        task.setTaskType("dummy_task_type");
        task.setWorkflowInstanceId("workflowInstanceId");
        task.setWorkflowType("workflowType");
        task.setResponseTimeoutSeconds(11L);
        task.setStatus(Status.COMPLETED);
        task.setRetryCount(0);
        task.setPollCount(0);
        task.setTaskId("taskId");
        task.setWorkflowTask(new WorkflowTask());
        task.setDomain("domain");
        task.setInputMessage(Any.getDefaultInstance());
        task.setOutputMessage(Any.getDefaultInstance());
        task.setRateLimitPerFrequency(11);
        task.setRateLimitFrequencyInSeconds(11);
        task.setExternalInputPayloadStoragePath("externalInputPayloadStoragePath");
        task.setExternalOutputPayloadStoragePath("externalOutputPayloadStoragePath");
        task.setWorkflowPriority(0);
        task.setIteration(1);
        task.setExecutionNameSpace("name_space");
        task.setIsolationGroupId("groupId");
        task.setStartTime(12L);
        task.setEndTime(20L);
        task.setScheduledTime(7L);
        task.setRetried(false);
        task.setReasonForIncompletion("");
        task.setWorkerId("");
        task.setSubWorkflowId("");
        task.setSubworkflowChanged(false);
        task.setExecutionMetadata(executionMetadata);

        final Task copy = task.deepCopy();
        assertEquals(task, copy);

        // Verify execution metadata is copied
        assertNotNull(copy.getExecutionMetadata());
        assertEquals(Long.valueOf(1000L), copy.getOrCreateExecutionMetadata().getServerSendTime());
        assertEquals(
                Long.valueOf(2000L), copy.getOrCreateExecutionMetadata().getClientReceiveTime());
        assertEquals(
                Long.valueOf(3000L), copy.getOrCreateExecutionMetadata().getExecutionStartTime());
        assertEquals(
                Long.valueOf(4000L), copy.getOrCreateExecutionMetadata().getExecutionEndTime());
        assertEquals(Long.valueOf(5000L), copy.getOrCreateExecutionMetadata().getClientSendTime());
        assertEquals(
                Long.valueOf(6000L), copy.getOrCreateExecutionMetadata().getPollNetworkLatency());
        assertEquals(
                Long.valueOf(7000L), copy.getOrCreateExecutionMetadata().getUpdateNetworkLatency());
    }

    @Test
    public void testRuntimeMetadataGetterSetterRoundTrip() {
        Task task = new Task();
        assertNotNull(task.getRuntimeMetadata());
        assertTrue(task.getRuntimeMetadata().isEmpty());

        Map<String, String> runtimeMetadata = new HashMap<>();
        runtimeMetadata.put("OPENAI_API_KEY", "sk-secret-value");
        task.setRuntimeMetadata(runtimeMetadata);

        assertEquals(runtimeMetadata, task.getRuntimeMetadata());
    }

    @Test
    public void testSetRuntimeMetadataNullGuard() {
        Task task = new Task();
        task.setRuntimeMetadata(null);
        assertNotNull(task.getRuntimeMetadata());
        assertTrue(task.getRuntimeMetadata().isEmpty());
    }

    @Test
    public void testRuntimeMetadataSerializedOnlyWhenNonEmpty() throws Exception {
        Task task = new Task();
        task.setTaskId("task-1");
        Map<String, String> runtimeMetadata = new HashMap<>();
        runtimeMetadata.put("OPENAI_API_KEY", "sk-secret-value");
        task.setRuntimeMetadata(runtimeMetadata);

        String json = objectMapper.writeValueAsString(task);
        assertTrue(json.contains("\"runtimeMetadata\""));
        assertTrue(json.contains("OPENAI_API_KEY"));
        assertTrue(json.contains("sk-secret-value"));

        Task emptyRuntimeMetadataTask = new Task();
        emptyRuntimeMetadataTask.setTaskId("task-2");
        String emptyJson = objectMapper.writeValueAsString(emptyRuntimeMetadataTask);
        assertFalse(emptyJson.contains("\"runtimeMetadata\""));
    }

    @Test
    public void testRuntimeMetadataExcludedFromEquals() {
        Task task1 = new Task();
        task1.setTaskId("task-1");
        Map<String, String> runtimeMetadata1 = new HashMap<>();
        runtimeMetadata1.put("OPENAI_API_KEY", "sk-secret-value-1");
        task1.setRuntimeMetadata(runtimeMetadata1);

        Task task2 = new Task();
        task2.setTaskId("task-1");
        Map<String, String> runtimeMetadata2 = new HashMap<>();
        runtimeMetadata2.put("OPENAI_API_KEY", "sk-secret-value-2");
        task2.setRuntimeMetadata(runtimeMetadata2);

        assertEquals(task1, task2);
        assertEquals(task1.hashCode(), task2.hashCode());
    }

    @Test
    public void testRuntimeMetadataExcludedFromToString() {
        Task task = new Task();
        task.setTaskId("task-1");
        Map<String, String> runtimeMetadata = new HashMap<>();
        runtimeMetadata.put("OPENAI_API_KEY", "sk-super-secret-value");
        task.setRuntimeMetadata(runtimeMetadata);

        assertFalse(task.toString().contains("sk-super-secret-value"));
    }

    @Test
    public void testExecutionMetadataHelperGettersNotSerialized() throws Exception {
        Task task = new Task();
        task.setTaskId("task-1");

        ExecutionMetadata executionMetadata = new ExecutionMetadata();
        executionMetadata.setServerSendTime(1000L);
        executionMetadata.setClientReceiveTime(2000L);
        task.setExecutionMetadata(executionMetadata);

        String json = objectMapper.writeValueAsString(task);

        // The convenience/helper getters must NOT leak as JSON properties.
        assertFalse(json.contains("orCreateExecutionMetadata"));
        assertFalse(json.contains("executionMetadataIfHasData"));

        // The real executionMetadata property is still serialized and round-trips.
        assertTrue(json.contains("\"executionMetadata\""));
        assertTrue(json.contains("\"serverSendTime\""));

        Task deserialized = objectMapper.readValue(json, Task.class);
        assertNotNull(deserialized.getExecutionMetadata());
        assertEquals(Long.valueOf(1000L), deserialized.getExecutionMetadata().getServerSendTime());
        assertEquals(
                Long.valueOf(2000L), deserialized.getExecutionMetadata().getClientReceiveTime());
    }

    @Test
    public void testRuntimeMetadataExcludedFromCopy() {
        Task task = new Task();
        task.setTaskId("task-1");
        Map<String, String> runtimeMetadata = new HashMap<>();
        runtimeMetadata.put("OPENAI_API_KEY", "sk-secret-value");
        task.setRuntimeMetadata(runtimeMetadata);

        Task copy = task.copy();
        assertTrue(copy.getRuntimeMetadata().isEmpty());

        Task deepCopy = task.deepCopy();
        assertTrue(deepCopy.getRuntimeMetadata().isEmpty());
    }
}
