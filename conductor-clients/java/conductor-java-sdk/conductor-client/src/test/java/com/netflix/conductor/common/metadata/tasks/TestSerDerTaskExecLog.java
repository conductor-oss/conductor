package com.netflix.conductor.common.metadata.tasks;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class TestSerDerTaskExecLog {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String serverJSON = "{\"log\":\"sample_log\",\"createdTime\":123,\"taskId\":\"sample_taskId\"}";

    @Test
    public void testSerializationDeserialization() throws Exception {
        TaskExecLog taskExecLog = objectMapper.readValue(serverJSON, TaskExecLog.class);
        assertEquals("sample_log", taskExecLog.getLog());
        assertEquals("sample_taskId", taskExecLog.getTaskId());
        assertEquals(123, taskExecLog.getCreatedTime());

        String serializedJSON = objectMapper.writeValueAsString(taskExecLog);
        assertEquals(objectMapper.readTree(serverJSON), objectMapper.readTree(serializedJSON));
    }
}