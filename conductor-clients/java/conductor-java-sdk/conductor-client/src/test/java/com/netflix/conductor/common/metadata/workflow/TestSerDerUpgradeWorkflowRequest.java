import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import com.netflix.conductor.common.metadata.workflow.UpgradeWorkflowRequest

public class TestSerDerUpgradeWorkflowRequest {

    @Test
    public void testSerializationDeserialization() throws Exception {
        String serverJSON = "{\"workflowInput\":{\"sample_key\":\"sample_value\"},\"name\":\"sample_name\",\"taskOutput\":{\"sample_key\":\"sample_value\"},\"version\":123}"

        ObjectMapper mapper = new ObjectMapper()

        // Deserialize JSON to POJO
        UpgradeWorkflowRequest pojo = mapper.readValue(serverJSON, UpgradeWorkflowRequest.class)

        // Assert that fields are correctly populated
        Assertions.assertEquals("sample_name", pojo.getName())
        Assertions.assertEquals(123, pojo.getVersion())
        Assertions.assertNotNull(pojo.getWorkflowInput())
        Assertions.assertEquals("sample_value", pojo.getWorkflowInput().get("sample_key"))
        Assertions.assertNotNull(pojo.getTaskOutput())
        Assertions.assertEquals("sample_value", pojo.getTaskOutput().get("sample_key"))

        // Serialize POJO back to JSON
        String serializedJSON = mapper.writeValueAsString(pojo)

        // Compare original and serialized JSON
        JsonNode original = mapper.readTree(serverJSON)
        JsonNode serialized = mapper.readTree(serializedJSON)
        Assertions.assertEquals(original, serialized)
    }
}