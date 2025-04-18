import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class TestSerDerSubWorkflowParams {

    @Test
    public void testSerializationDeserialization() throws JsonProcessingException {
        String serverJSON = "{\"workflowDefinition\":\"sample_workflowDefinition\",\"idempotencyKey\":\"sample_idempotencyKey\",\"name\":\"sample_name\",\"taskToDomain\":{\"sample_key\":\"sample_value\"},\"priority\":\"sample_object_priority\",\"version\":123,\"idempotencyStrategy\":{\"values\":[\"FAIL\",\"RETURN_EXISTING\",\"FAIL_ON_RUNNING\"],\"constants\":{\"FAIL_ON_RUNNING\":\"(2)\",\"RETURN_EXISTING\":\"(1)\",\"FAIL\":\"(0)\"},\"sampleValue\":\"FAIL\"}}";

        ObjectMapper objectMapper = new ObjectMapper();
        SubWorkflowParams params = objectMapper.readValue(serverJSON, SubWorkflowParams.class);
        
        assertEquals("sample_workflowDefinition", params.getWorkflowDefinition());
        assertEquals("sample_idempotencyKey", params.getIdempotencyKey());
        assertEquals("sample_name", params.getName());
        assertEquals(123, params.getVersion());
        assertEquals("sample_object_priority", params.getPriority());
        assertNotNull(params.getTaskToDomain());
        assertEquals(1, params.getTaskToDomain().size());
        assertEquals("sample_value", params.getTaskToDomain().get("sample_key"));

        IdempotencyStrategy strategy = params.getIdempotencyStrategy();
        assertNotNull(strategy);
        assertEquals(3, strategy.getValues().size());
        assertTrue(strategy.getValues().contains("FAIL"));
        assertTrue(strategy.getValues().contains("RETURN_EXISTING"));
        assertTrue(strategy.getValues().contains("FAIL_ON_RUNNING"));
        assertEquals("(0)", strategy.getConstants().get("FAIL"));
        assertEquals("(1)", strategy.getConstants().get("RETURN_EXISTING"));
        assertEquals("(2)", strategy.getConstants().get("FAIL_ON_RUNNING"));
        assertEquals("FAIL", strategy.getSampleValue());

        String serializedJSON = objectMapper.writeValueAsString(params);
        assertEquals(objectMapper.readTree(serverJSON), objectMapper.readTree(serializedJSON));
    }
}