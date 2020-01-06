package com.netflix.conductor.core.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.run.ExternalStorageLocation;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.utils.ExternalPayloadStorage;
import com.netflix.conductor.common.utils.JsonMapperProvider;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.execution.TerminateWorkflowException;
import com.netflix.conductor.core.execution.TestConfiguration;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ExternalPayloadStorageUtilsTest {

    private ExternalPayloadStorage externalPayloadStorage;
    private ExternalStorageLocation location;
    private ObjectMapper objectMapper;

    // Subject
    private ExternalPayloadStorageUtils externalPayloadStorageUtils;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Before
    public void setup() {
        externalPayloadStorage = mock(ExternalPayloadStorage.class);
        Configuration configuration = new TestConfiguration();
        objectMapper = new JsonMapperProvider().get();
        location = new ExternalStorageLocation();
        location.setPath("some/test/path");

        externalPayloadStorageUtils = new ExternalPayloadStorageUtils(externalPayloadStorage, configuration, objectMapper);
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

        InputStream stream = ExternalPayloadStorageUtilsTest.class.getResourceAsStream("/payload.json");
        Map<String, Object> payload = objectMapper.readValue(stream, Map.class);

        when(externalPayloadStorage.getLocation(ExternalPayloadStorage.Operation.WRITE, ExternalPayloadStorage.PayloadType.TASK_INPUT, "")).thenReturn(location);
        doAnswer(invocation -> {
            uploadCount.incrementAndGet();
            return null;
        }).when(externalPayloadStorage).upload(anyString(), any(), anyLong());

        Task task = new Task();
        task.setInputData(payload);
        externalPayloadStorageUtils.verifyAndUpload(task, ExternalPayloadStorage.PayloadType.TASK_INPUT);
        assertNull(task.getInputData());
        assertEquals(1, uploadCount.get());
        assertNotNull(task.getExternalInputPayloadStoragePath());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testUploadWorkflowPayload() throws IOException {
        AtomicInteger uploadCount = new AtomicInteger(0);

        InputStream stream = ExternalPayloadStorageUtilsTest.class.getResourceAsStream("/payload.json");
        Map<String, Object> payload = objectMapper.readValue(stream, Map.class);

        when(externalPayloadStorage.getLocation(ExternalPayloadStorage.Operation.WRITE, ExternalPayloadStorage.PayloadType.WORKFLOW_OUTPUT, "")).thenReturn(location);
        doAnswer(invocation -> {
            uploadCount.incrementAndGet();
            return null;
        }).when(externalPayloadStorage).upload(anyString(), any(), anyLong());

        Workflow workflow = new Workflow();
        workflow.setOutput(payload);
        externalPayloadStorageUtils.verifyAndUpload(workflow, ExternalPayloadStorage.PayloadType.WORKFLOW_OUTPUT);
        assertNull(workflow.getOutput());
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

        assertEquals(path, externalPayloadStorageUtils.uploadHelper(new byte[]{}, 10L, ExternalPayloadStorage.PayloadType.TASK_OUTPUT));
        assertEquals(1, uploadCount.get());
    }

    @Test
    public void testFailTaskWithInputPayload() {
        Task task = new Task();
        task.setInputData(new HashMap<>());

        expectedException.expect(TerminateWorkflowException.class);
        externalPayloadStorageUtils.failTask(task, ExternalPayloadStorage.PayloadType.TASK_INPUT, "error");
        assertNotNull(task);
        assertNull(task.getInputData());
    }

    @Test
    public void testFailTaskWithOutputPayload() {
        Task task = new Task();
        task.setOutputData(new HashMap<>());

        expectedException.expect(TerminateWorkflowException.class);
        externalPayloadStorageUtils.failTask(task, ExternalPayloadStorage.PayloadType.TASK_OUTPUT, "error");
        assertNotNull(task);
        assertNull(task.getOutputData());
    }
}
