package com.netflix.conductor.common.metadata.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class TestSerDerRerunWorkflowRequest {

    @Test
    public void testSerializationDeserialization() throws Exception {
        String serverJson = "{\"workflowInput\":{\"sample_key\":\"sample_value\"},\"reRunFromWorkflowId\":\"sample_reRunFromWorkflowId\",\"taskInput\":{\"sample_key\":\"sample_value\"},\"correlationId\":\"sample_correlationId\",\"reRunFromTaskId\":\"sample_reRunFromTaskId\"}";

        ObjectMapper mapper = new ObjectMapper();
        RerunWorkflowRequest request = mapper.readValue(serverJson, RerunWorkflowRequest.class);

        assertEquals("sample_reRunFromWorkflowId", request.getReRunFromWorkflowId());
        assertEquals("sample_reRunFromTaskId", request.getReRunFromTaskId());
        assertEquals("sample_correlationId", request.getCorrelationId());
        assertNotNull(request.getWorkflowInput());
        assertEquals("sample_value", request.getWorkflowInput().get("sample_key"));
        assertNotNull(request.getTaskInput());
        assertEquals("sample_value", request.getTaskInput().get("sample_key"));

        String serializedJson = mapper.writeValueAsString(request);

        JsonNode originalJsonNode = mapper.readTree(serverJson);
        JsonNode serializedJsonNode = mapper.readTree(serializedJson);

        assertEquals(originalJsonNode, serializedJsonNode);
    }
}