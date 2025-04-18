import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.orkes.conductor.client.model.integration.ai.PromptTemplateTestRequest
import org.junit.jupiter.api.Test
import static org.junit.jupiter.api.Assertions.*

class TestSerDerPromptTemplateTestRequest {

    @Test
    void testSerializationDeserialization() throws Exception {
        String serverJSON = "{\"promptVariables\":{\"key\":\"sample_value\"},\"stopWords\":[\"sample_stopWords\"],\"llmProvider\":\"sample_llmProvider\",\"temperature\":123.456,\"model\":\"sample_model\",\"prompt\":\"sample_prompt\",\"topP\":123.456}"
        ObjectMapper objectMapper = new ObjectMapper()
        
        // Deserialize JSON to POJO
        PromptTemplateTestRequest request = objectMapper.readValue(serverJSON, PromptTemplateTestRequest.class)
        
        // Assert fields
        assertEquals("sample_llmProvider", request.getLlmProvider())
        assertEquals("sample_model", request.getModel())
        assertEquals("sample_prompt", request.getPrompt())
        assertEquals(123.456, request.getTemperature())
        assertEquals(123.456, request.getTopP())
        assertNotNull(request.getPromptVariables())
        assertEquals("sample_value", request.getPromptVariables().get("key"))
        assertNotNull(request.getStopWords())
        assertEquals(1, request.getStopWords().size())
        assertEquals("sample_stopWords", request.getStopWords().get(0))
        
        // Serialize POJO back to JSON
        String serializedJSON = objectMapper.writeValueAsString(request)
        
        // Compare original JSON and serialized JSON
        JsonNode originalJsonNode = objectMapper.readTree(serverJSON)
        JsonNode serializedJsonNode = objectMapper.readTree(serializedJSON)
        assertEquals(originalJsonNode, serializedJsonNode)
    }
}