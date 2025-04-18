package io.orkes.conductor.client.model.integration.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class TestSerDerLLMResponse {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testSerializationDeserialization() throws Exception {
        String serverJSON = "{\"result\":\"sample_result\",\"finishReason\":\"sample_finishReason\",\"tokenUsed\":123}";
        LLMResponse response = objectMapper.readValue(serverJSON, LLMResponse.class);
        
        assertEquals("sample_result", response.getResult());
        assertEquals("sample_finishReason", response.getFinishReason());
        assertEquals(123, response.getTokenUsed());
        
        String serializedJSON = objectMapper.writeValueAsString(response);
        JsonNode original = objectMapper.readTree(serverJSON);
        JsonNode serialized = objectMapper.readTree(serializedJSON);
        
        assertEquals(original, serialized);
    }
}