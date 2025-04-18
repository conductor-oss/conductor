package com.netflix.conductor.common.metadata.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class TestSerDerSkipTaskRequest {
    @Test
    public void testSerializationDeserialization() throws JsonProcessingException {
        String serverJSON = "{\"taskInput\":{\"sample_key\":\"sample_value\"},\"taskOutput\":{\"sample_key\":\"sample_value\"}}";
        ObjectMapper mapper = new ObjectMapper();
        SkipTaskRequest request = mapper.readValue(serverJSON, SkipTaskRequest.class);
        assertNotNull(request);
        assertNotNull(request.getTaskInput());
        assertEquals("sample_value", request.getTaskInput().get("sample_key"));
        assertNotNull(request.getTaskOutput());
        assertEquals("sample_value", request.getTaskOutput().get("sample_key"));
        String serializedJSON = mapper.writeValueAsString(request);
        assertEquals(mapper.readTree(serverJSON), mapper.readTree(serializedJSON));
    }
}