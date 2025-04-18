import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.orkes.conductor.client.model.integration.ai.LLMWorkerInput;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestSerDerLLMWorkerInput {

    @Test
    public void testSerializationDeserialization() throws Exception {
        String serverJSON = "{\"stopWords\":[\"sample_stopWords\"],\"llmProvider\":\"sample_llmProvider\",\"maxResults\":123,\"temperature\":123.456,\"maxTokens\":123,\"embeddingModelProvider\":\"sample_embeddingModelProvider\",\"model\":\"sample_model\",\"embeddingModel\":\"sample_embeddingModel\",\"prompt\":\"sample_prompt\",\"topP\":123.456}";
        ObjectMapper objectMapper = new ObjectMapper();
        LLMWorkerInput pojo = objectMapper.readValue(serverJSON, LLMWorkerInput.class);
        Assertions.assertEquals("sample_llmProvider", pojo.getLlmProvider());
        Assertions.assertEquals("sample_model", pojo.getModel());
        Assertions.assertEquals("sample_embeddingModel", pojo.getEmbeddingModel());
        Assertions.assertEquals("sample_embeddingModelProvider", pojo.getEmbeddingModelProvider());
        Assertions.assertEquals("sample_prompt", pojo.getPrompt());
        Assertions.assertEquals(123.456, pojo.getTemperature());
        Assertions.assertEquals(123.456, pojo.getTopP());
        Assertions.assertNotNull(pojo.getStopWords());
        Assertions.assertEquals(1, pojo.getStopWords().size());
        Assertions.assertEquals("sample_stopWords", pojo.getStopWords().get(0));
        Assertions.assertEquals(123, pojo.getMaxTokens());
        Assertions.assertEquals(123, pojo.getMaxResults());
        String marshalledJSON = objectMapper.writeValueAsString(pojo);
        JsonNode originalNode = objectMapper.readTree(serverJSON);
        JsonNode marshalledNode = objectMapper.readTree(marshalledJSON);
        Assertions.assertEquals(originalNode, marshalledNode);
    }
}