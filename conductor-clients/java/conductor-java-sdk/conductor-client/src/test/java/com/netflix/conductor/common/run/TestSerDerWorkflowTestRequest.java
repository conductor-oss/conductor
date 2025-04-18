import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;

public class TestSerDerWorkflowTestRequest {

    private static final String serverJSON = "{\"subWorkflowTestRequest\":{},\"taskRefToMockOutput\":{\"sample_key\":[{\"output\":{\"sample_key\":\"sample_value\"},\"executionTime\":123,\"queueWaitTime\":123,\"status\":{\"values\":[\"IN_PROGRESS\",\"FAILED\",\"FAILED_WITH_TERMINAL_ERROR\",\"COMPLETED\"],\"constants\":{\"IN_PROGRESS\":\"(0)\",\"FAILED_WITH_TERMINAL_ERROR\":\"(2)\",\"COMPLETED\":\"(3)\",\"FAILED\":\"(1)\"},\"sampleValue\":\"IN_PROGRESS\"}}]}}";

    @Test
    public void testSerializationDeserialization() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        // Deserialize JSON to POJO
        WorkflowTestRequest request = mapper.readValue(serverJSON, WorkflowTestRequest.class);
        assertNotNull(request);
        
        // Assert subWorkflowTestRequest is empty
        assertNotNull(request.getSubWorkflowTestRequest());
        assertTrue(request.getSubWorkflowTestRequest().isEmpty());

        // Assert taskRefToMockOutput
        Map<String, List<WorkflowTestRequest.TaskMock>> taskRef = request.getTaskRefToMockOutput();
        assertNotNull(taskRef);
        assertTrue(taskRef.containsKey("sample_key"));
        List<WorkflowTestRequest.TaskMock> taskMocks = taskRef.get("sample_key");
        assertNotNull(taskMocks);
        assertEquals(1, taskMocks.size());

        WorkflowTestRequest.TaskMock taskMock = taskMocks.get(0);
        assertNotNull(taskMock);

        // Assert output
        Map<String, Object> output = taskMock.getOutput();
        assertNotNull(output);
        assertEquals("sample_value", output.get("sample_key"));

        // Assert executionTime and queueWaitTime
        assertEquals(123, taskMock.getExecutionTime());
        assertEquals(123, taskMock.getQueueWaitTime());

        // Assert status
        TaskResult.Status status = taskMock.getStatus();
        assertNotNull(status);
        assertEquals(TaskResult.Status.IN_PROGRESS, status);

        // Serialize POJO back to JSON
        String serializedJSON = mapper.writeValueAsString(request);

        // Compare JSON structures
        JsonNode originalNode = mapper.readTree(serverJSON);
        JsonNode serializedNode = mapper.readTree(serializedJSON);
        assertEquals(originalNode, serializedNode);
    }
}