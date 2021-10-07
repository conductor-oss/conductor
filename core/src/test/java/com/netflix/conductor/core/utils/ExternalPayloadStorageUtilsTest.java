/*
 *  Copyright 2021 Netflix, Inc.
 *  <p>
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 *  the License. You may obtain a copy of the License at
 *  <p>
 *  http://www.apache.org/licenses/LICENSE-2.0
 *  <p>
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 *  an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 *  specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.core.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.common.config.TestObjectMapperConfiguration;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.run.ExternalStorageLocation;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.utils.ExternalPayloadStorage;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.exception.TerminateWorkflowException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.unit.DataSize;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ContextConfiguration(classes = {TestObjectMapperConfiguration.class})
@RunWith(SpringRunner.class)
public class ExternalPayloadStorageUtilsTest {

    private ExternalPayloadStorage externalPayloadStorage;
    private ExternalStorageLocation location;

    @Autowired
    private ObjectMapper objectMapper;

    // Subject
    private ExternalPayloadStorageUtils externalPayloadStorageUtils;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Before
    public void setup() {
        externalPayloadStorage = mock(ExternalPayloadStorage.class);
        ConductorProperties properties = mock(ConductorProperties.class);
        location = new ExternalStorageLocation();
        location.setPath("some/test/path");

        when(properties.getWorkflowInputPayloadSizeThreshold()).thenReturn(DataSize.ofKilobytes(10L));
        when(properties.getMaxWorkflowInputPayloadSizeThreshold()).thenReturn(DataSize.ofKilobytes(10240L));
        when(properties.getWorkflowOutputPayloadSizeThreshold()).thenReturn(DataSize.ofKilobytes(10L));
        when(properties.getMaxWorkflowOutputPayloadSizeThreshold()).thenReturn(DataSize.ofKilobytes(10240L));
        when(properties.getTaskInputPayloadSizeThreshold()).thenReturn(DataSize.ofKilobytes(10L));
        when(properties.getMaxTaskInputPayloadSizeThreshold()).thenReturn(DataSize.ofKilobytes(10240L));
        when(properties.getTaskOutputPayloadSizeThreshold()).thenReturn(DataSize.ofKilobytes(10L));
        when(properties.getMaxTaskOutputPayloadSizeThreshold()).thenReturn(DataSize.ofKilobytes(10240L));

        externalPayloadStorageUtils = new ExternalPayloadStorageUtils(externalPayloadStorage, properties,
            objectMapper);
    }

    @Test
    public void testDownloadPayload() throws IOException {
        String path = "test/payload";

        Map<String, Object> payload = new HashMap<>();
        payload.put("key1", "value1");
        payload.put("key2", 200);
        byte[] payloadBytes = objectMapper.writeValueAsString(payload).getBytes();
        when(externalPayloadStorage.download(path)).thenReturn(new ByteArrayInputStream(payloadBytes));

        Map<String, Object> result = externalPayloadStorageUtils.downloadPayload(path);
        assertNotNull(result);
        assertEquals(payload, result);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testUploadTaskPayload() throws IOException {
        AtomicInteger uploadCount = new AtomicInteger(0);

        InputStream stream = com.netflix.conductor.core.utils.ExternalPayloadStorageUtilsTest.class
            .getResourceAsStream("/payload.json");
        Map<String, Object> payload = objectMapper.readValue(stream, Map.class);

        when(externalPayloadStorage
            .getLocation(ExternalPayloadStorage.Operation.WRITE, ExternalPayloadStorage.PayloadType.TASK_INPUT, ""))
            .thenReturn(location);
        doAnswer(invocation -> {
            uploadCount.incrementAndGet();
            return null;
        }).when(externalPayloadStorage).upload(anyString(), any(), anyLong());

        Task task = new Task();
        task.setInputData(payload);
        externalPayloadStorageUtils.verifyAndUpload(task, ExternalPayloadStorage.PayloadType.TASK_INPUT);
        assertTrue(task.getInputData().isEmpty());
        assertEquals(1, uploadCount.get());
        assertNotNull(task.getExternalInputPayloadStoragePath());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testUploadWorkflowPayload() throws IOException {
        AtomicInteger uploadCount = new AtomicInteger(0);

        InputStream stream = com.netflix.conductor.core.utils.ExternalPayloadStorageUtilsTest.class
            .getResourceAsStream("/payload.json");
        Map<String, Object> payload = objectMapper.readValue(stream, Map.class);

        when(externalPayloadStorage
            .getLocation(ExternalPayloadStorage.Operation.WRITE, ExternalPayloadStorage.PayloadType.WORKFLOW_OUTPUT,
                "")).thenReturn(location);
        doAnswer(invocation -> {
            uploadCount.incrementAndGet();
            return null;
        }).when(externalPayloadStorage).upload(anyString(), any(), anyLong());

        Workflow workflow = new Workflow();
        WorkflowDef def = new WorkflowDef();
        def.setName("name");
        def.setVersion(1);
        workflow.setOutput(payload);
        workflow.setWorkflowDefinition(def);
        externalPayloadStorageUtils.verifyAndUpload(workflow, ExternalPayloadStorage.PayloadType.WORKFLOW_OUTPUT);
        assertTrue(workflow.getOutput().isEmpty());
        assertEquals(1, uploadCount.get());
        assertNotNull(workflow.getExternalOutputPayloadStoragePath());
    }

    @Test
    public void testUploadHelper() {
        AtomicInteger uploadCount = new AtomicInteger(0);
        String path = "some/test/path.json";
        ExternalStorageLocation location = new ExternalStorageLocation();
        location.setPath(path);

        when(externalPayloadStorage.getLocation(any(), any(), any())).thenReturn(location);
        doAnswer(invocation -> {
            uploadCount.incrementAndGet();
            return null;
        }).when(externalPayloadStorage).upload(anyString(), any(), anyLong());

        assertEquals(path, externalPayloadStorageUtils
            .uploadHelper(new byte[]{}, 10L, ExternalPayloadStorage.PayloadType.TASK_OUTPUT));
        assertEquals(1, uploadCount.get());
    }

    @Test
    public void testFailTaskWithInputPayload() {
        Task task = new Task();
        task.setInputData(new HashMap<>());

        expectedException.expect(TerminateWorkflowException.class);
        externalPayloadStorageUtils.failTask(task, ExternalPayloadStorage.PayloadType.TASK_INPUT, "error");
        assertNotNull(task);
        assertTrue(task.getInputData().isEmpty());
    }

    @Test
    public void testFailTaskWithOutputPayload() {
        Task task = new Task();
        task.setOutputData(new HashMap<>());

        expectedException.expect(TerminateWorkflowException.class);
        externalPayloadStorageUtils.failTask(task, ExternalPayloadStorage.PayloadType.TASK_OUTPUT, "error");
        assertNotNull(task);
        assertTrue(task.getOutputData().isEmpty());
    }

    @Test
    public void testFailWorkflowWithInputPayload() {
        Workflow workflow = new Workflow();
        workflow.setInput(new HashMap<>());

        expectedException.expect(TerminateWorkflowException.class);
        externalPayloadStorageUtils.failWorkflow(workflow, ExternalPayloadStorage.PayloadType.TASK_INPUT, "error");
        assertNotNull(workflow);
        assertTrue(workflow.getInput().isEmpty());
        assertEquals(Workflow.WorkflowStatus.FAILED, workflow.getStatus());
    }

    @Test
    public void testFailWorkflowWithOutputPayload() {
        Workflow workflow = new Workflow();
        workflow.setOutput(new HashMap<>());

        expectedException.expect(TerminateWorkflowException.class);
        externalPayloadStorageUtils.failWorkflow(workflow, ExternalPayloadStorage.PayloadType.TASK_OUTPUT, "error");
        assertNotNull(workflow);
        assertTrue(workflow.getOutput().isEmpty());
        assertEquals(Workflow.WorkflowStatus.FAILED, workflow.getStatus());
    }
}
