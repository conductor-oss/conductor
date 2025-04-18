import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

public class TestSerDerEmbeddingRequest {

    private static final ObjectMapper objectMapper = new ObjectMapper()

    @Test
    public void testSerializationDeserialization() throws Exception {
        String serverJSON = "{\"llmProvider\":\"sample_llmProvider\",\"model\":\"sample_model\",\"text\":\"sample_text\",\"dimensions\":123}"

        EmbeddingRequest request = objectMapper.readValue(serverJSON, EmbeddingRequest.class)

        Assertions.assertEquals("sample_llmProvider", request.getLlmProvider())
        Assertions.assertEquals("sample_model", request.getModel())
        Assertions.assertEquals("sample_text", request.getText())
        Assertions.assertEquals(123, request.getDimensions())

        String serializedJSON = objectMapper.writeValueAsString(request)

        JsonNode originalJson = objectMapper.readTree(serverJSON)
        JsonNode serializedJson = objectMapper.readTree(serializedJSON)

        Assertions.assertEquals(originalJson, serializedJson)
    }
}