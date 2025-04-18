import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import io.orkes.conductor.client.model.WorkflowStatus;
import java.util.Map;

public class TestSerDerWorkflowStatus {
    @Test
    public void testSerializationDeserialization() throws Exception {
        String serverJSON = "{\"output\":{\"key\":\"sample_value\"},\"variables\":{\"key\":\"sample_value\"},\"correlationId\":\"sample_correlationId\",\"workflowId\":\"sample_workflowId\",\"status\":{\"values\":[\"RUNNING\",\"COMPLETED\",\"FAILED\",\"TIMED_OUT\",\"TERMINATED\",\"PAUSED\"],\"constants\":{\"PAUSED\":\"(false, true)\",\"COMPLETED\":\"(true, true)\",\"FAILED\":\"(true, false)\",\"RUNNING\":\"(false, false)\",\"TIMED_OUT\":\"(true, false)\",\"TERMINATED\":\"(true, false)\"},\"sampleValue\":\"RUNNING\"}}";
        ObjectMapper mapper = new ObjectMapper();
        WorkflowStatus workflowStatus = mapper.readValue(serverJSON, WorkflowStatus.class);
        
        assertEquals("sample_correlationId", workflowStatus.getCorrelationId());
        assertEquals("sample_workflowId", workflowStatus.getWorkflowId());
        
        Map<String, Object> output = workflowStatus.getOutput();
        assertNotNull(output);
        assertEquals("sample_value", output.get("key"));
        
        Map<String, Object> variables = workflowStatus.getVariables();
        assertNotNull(variables);
        assertEquals("sample_value", variables.get("key"));
        
        assertEquals(WorkflowStatus.StatusEnum.RUNNING, workflowStatus.getStatus());
        
        String serializedJSON = mapper.writeValueAsString(workflowStatus);
        JsonNode originalNode = mapper.readTree(serverJSON);
        JsonNode serializedNode = mapper.readTree(serializedJSON);
        
        assertEquals(originalNode.get("output"), serializedNode.get("output"));
        assertEquals(originalNode.get("variables"), serializedNode.get("variables"));
        assertEquals(originalNode.get("correlationId"), serializedNode.get("correlationId"));
        assertEquals(originalNode.get("workflowId"), serializedNode.get("workflowId"));
        assertEquals(originalNode.get("status").get("sampleValue").asText(), serializedNode.get("status").asText());
    }
}