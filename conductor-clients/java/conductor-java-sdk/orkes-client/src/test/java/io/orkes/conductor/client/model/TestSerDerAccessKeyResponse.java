package io.orkes.conductor.client.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class TestSerDerAccessKeyResponse {

    @Test
    public void testSerializationDeserialization() throws Exception {
        String serverJSON = "{\"createdAt\":123,\"id\":\"sample_id\",\"status\":{\"values\":[\"ACTIVE\",\"INACTIVE\"],\"constants\":{\"ACTIVE\":\"(0)\",\"INACTIVE\":\"(1)\"},\"sampleValue\":\"ACTIVE\"}}";

        ObjectMapper mapper = new ObjectMapper();

        AccessKeyResponse response = mapper.readValue(serverJSON, AccessKeyResponse.class);

        assertEquals("sample_id", response.getId());
        assertEquals(123L, response.getCreatedAt());

        AccessKeyStatus status = response.getStatus();
        assertNotNull(status);
        assertEquals(List.of("ACTIVE", "INACTIVE"), status.getValues());
        assertEquals(Map.of("ACTIVE", "(0)", "INACTIVE", "(1)"), status.getConstants());
        assertEquals("ACTIVE", status.getSampleValue());

        String serializedJSON = mapper.writeValueAsString(response);

        JsonNode originalJson = mapper.readTree(serverJSON);
        JsonNode serializedJson = mapper.readTree(serializedJSON);

        assertEquals(originalJson, serializedJson);
    }
}