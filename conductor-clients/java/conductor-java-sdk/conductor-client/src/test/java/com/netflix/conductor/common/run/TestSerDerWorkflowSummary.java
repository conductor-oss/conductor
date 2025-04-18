import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import static org.junit.jupiter.api.Assertions.*
import java.util.Arrays
import java.util.HashSet

public class TestSerDerWorkflowSummary {
    private static final ObjectMapper objectMapper = new ObjectMapper()

    @Test
    public void testSerializationDeserialization() throws Exception {
        String serverJSON = "{\"externalInputPayloadStoragePath\":\"sample_externalInputPayloadStoragePath\",\"updateTime\":\"sample_updateTime\",\"priority\":123,\"version\":123,\"failedReferenceTaskNames\":\"sample_failedReferenceTaskNames\",\"output\":\"sample_output\",\"executionTime\":123,\"input\":\"sample_input\",\"failedTaskNames\":[\"sample_failedTaskNames\"],\"createdBy\":\"sample_createdBy\",\"reasonForIncompletion\":\"sample_reasonForIncompletion\",\"workflowType\":\"sample_workflowType\",\"correlationId\":\"sample_correlationId\",\"startTime\":\"sample_startTime\",\"endTime\":\"sample_endTime\",\"event\":\"sample_event\",\"workflowId\":\"sample_workflowId\",\"status\":{\"values\":[\"RUNNING\",\"COMPLETED\",\"FAILED\",\"TIMED_OUT\",\"TERMINATED\",\"PAUSED\"],\"constants\":{\"PAUSED\":\"(false, true)\",\"COMPLETED\":\"(true, true)\",\"FAILED\":\"(true, false)\",\"RUNNING\":\"(false, false)\",\"TIMED_OUT\":\"(true, false)\",\"TERMINATED\":\"(true, false)\"},\"sampleValue\":\"RUNNING\"},\"externalOutputPayloadStoragePath\":\"sample_externalOutputPayloadStoragePath\"}"

        // Deserialize JSON to POJO
        WorkflowSummary workflowSummary = objectMapper.readValue(serverJSON, WorkflowSummary.class)

        // Assertions for primitive and string fields
        assertEquals("sample_externalInputPayloadStoragePath", workflowSummary.getExternalInputPayloadStoragePath())
        assertEquals("sample_updateTime", workflowSummary.getUpdateTime())
        assertEquals(123, workflowSummary.getPriority())
        assertEquals(123, workflowSummary.getVersion())
        assertEquals("sample_failedReferenceTaskNames", workflowSummary.getFailedReferenceTaskNames())
        assertEquals("sample_output", workflowSummary.getOutput())
        assertEquals(123L, workflowSummary.getExecutionTime())
        assertEquals("sample_input", workflowSummary.getInput())
        assertEquals("sample_createdBy", workflowSummary.getCreatedBy())
        assertEquals("sample_reasonForIncompletion", workflowSummary.getReasonForIncompletion())
        assertEquals("sample_workflowType", workflowSummary.getWorkflowType())
        assertEquals("sample_correlationId", workflowSummary.getCorrelationId())
        assertEquals("sample_startTime", workflowSummary.getStartTime())
        assertEquals("sample_endTime", workflowSummary.getEndTime())
        assertEquals("sample_event", workflowSummary.getEvent())
        assertEquals("sample_workflowId", workflowSummary.getWorkflowId())

        // Assertions for list fields
        assertNotNull(workflowSummary.getFailedTaskNames())
        assertEquals(1, workflowSummary.getFailedTaskNames().size())
        assertTrue(workflowSummary.getFailedTaskNames().contains("sample_failedTaskNames"))

        // Assertions for status field
        assertNotNull(workflowSummary.getStatus())
        assertEquals(Arrays.asList("RUNNING", "COMPLETED", "FAILED", "TIMED_OUT", "TERMINATED", "PAUSED"), workflowSummary.getStatus().getValues())
        assertEquals(6, workflowSummary.getStatus().getConstants().size())
        assertEquals("(false, true)", workflowSummary.getStatus().getConstants().get("PAUSED"))
        assertEquals("(true, true)", workflowSummary.getStatus().getConstants().get("COMPLETED"))
        assertEquals("(true, false)", workflowSummary.getStatus().getConstants().get("FAILED"))
        assertEquals("(false, false)", workflowSummary.getStatus().getConstants().get("RUNNING"))
        assertEquals("(true, false)", workflowSummary.getStatus().getConstants().get("TIMED_OUT"))
        assertEquals("(true, false)", workflowSummary.getStatus().getConstants().get("TERMINATED"))
        assertEquals("RUNNING", workflowSummary.getStatus().getSampleValue())

        // Serialize POJO back to JSON
        String serializedJSON = objectMapper.writeValueAsString(workflowSummary)

        // Compare the original and serialized JSON as JsonNode to ignore ordering
        JsonNode originalNode = objectMapper.readTree(serverJSON)
        JsonNode serializedNode = objectMapper.readTree(serializedJSON)
        assertEquals(originalNode, serializedNode)
    }
}