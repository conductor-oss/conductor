import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.JsonNode
import io.orkes.conductor.client.model.CreateOrUpdateApplicationRequest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

public class TestSerDerCreateOrUpdateApplicationRequest {

    private final ObjectMapper objectMapper = new ObjectMapper()

    @Test
    public void testSerializationDeserialization() throws Exception {
        String serverJSON = "{\"name\":\"sample_name\"}"
        CreateOrUpdateApplicationRequest request = objectMapper.readValue(serverJSON, CreateOrUpdateApplicationRequest.class)
        Assertions.assertEquals("sample_name", request.getName())
        String serializedJSON = objectMapper.writeValueAsString(request)
        JsonNode originalJson = objectMapper.readTree(serverJSON)
        JsonNode serializedJson = objectMapper.readTree(serializedJSON)
        Assertions.assertEquals(originalJson, serializedJson)
    }
}