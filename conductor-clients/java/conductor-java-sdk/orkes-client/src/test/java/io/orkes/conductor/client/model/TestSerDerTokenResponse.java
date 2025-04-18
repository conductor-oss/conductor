package io.orkes.conductor.client.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class TestSerDerTokenResponse {

    @Test
    public void testSerializationDeserialization() throws Exception {
        String serverJSON = "{\"token\":\"sample_token\"}";
        ObjectMapper objectMapper = new ObjectMapper();

        // Deserialize
        TokenResponse tokenResponse = objectMapper.readValue(serverJSON, TokenResponse.class);
        assertNotNull(tokenResponse);
        assertEquals("sample_token", tokenResponse.getToken());

        // Serialize
        String serializedJSON = objectMapper.writeValueAsString(tokenResponse);

        // Compare
        assertEquals(objectMapper.readTree(serverJSON), objectMapper.readTree(serializedJSON));
    }
}