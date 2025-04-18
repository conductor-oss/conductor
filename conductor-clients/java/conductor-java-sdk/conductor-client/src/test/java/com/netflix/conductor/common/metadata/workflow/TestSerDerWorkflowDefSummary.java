package com.netflix.conductor.common.metadata.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestSerDerWorkflowDefSummary {

    @Test
    public void testSerializationDeserialization() throws Exception {
        String serverJson = "{\"createTime\":123,\"name\":\"sample_name\",\"version\":123}";
        ObjectMapper objectMapper = new ObjectMapper();
        WorkflowDefSummary obj = objectMapper.readValue(serverJson, WorkflowDefSummary.class);
        Assertions.assertEquals("sample_name", obj.getName());
        Assertions.assertEquals(123, obj.getVersion());
        Assertions.assertEquals(123L, obj.getCreateTime());

        String serializedJson = objectMapper.writeValueAsString(obj);
        JsonNode originalJson = objectMapper.readTree(serverJson);
        JsonNode serializedJsonNode = objectMapper.readTree(serializedJson);
        Assertions.assertEquals(originalJson, serializedJsonNode);
    }
}