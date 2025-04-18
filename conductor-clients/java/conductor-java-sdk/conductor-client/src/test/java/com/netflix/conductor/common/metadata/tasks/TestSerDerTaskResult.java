import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.tasks.TaskExecLog;
import java.util.List;
import java.util.Map;

public class TestSerDerTaskResult {

    @Test
    public void testSerializationDeserialization() throws Exception {
        String serverJSON = "{\"outputData\":{\"sample_key\":\"sample_value\"},\"extendLease\":true,\"callbackAfterSeconds\":123,\"workerId\":\"sample_workerId\",\"subWorkflowId\":null,\"reasonForIncompletion\":\"sample_reasonForIncompletion\",\"workflowInstanceId\":\"sample_workflowInstanceId\",\"logs\":[{\"log\":\"sample_log\",\"createdTime\":123,\"taskId\":\"sample_taskId\"}],\"taskId\":\"sample_taskId\",\"status\":{\"values\":[\"IN_PROGRESS\",\"FAILED\",\"FAILED_WITH_TERMINAL_ERROR\",\"COMPLETED\"],\"constants\":{\"IN_PROGRESS\":\"(0)\",\"FAILED_WITH_TERMINAL_ERROR\":\"(2)\",\"COMPLETED\":\"(3)\",\"FAILED\":\"(1)\"},\"sampleValue\":\"IN_PROGRESS\"},\"externalOutputPayloadStoragePath\":\"sample_externalOutputPayloadStoragePath\"}";
        
        ObjectMapper mapper = new ObjectMapper();
        TaskResult taskResult = mapper.readValue(serverJSON, TaskResult.class);
        
        assertEquals("sample_workflowInstanceId", taskResult.getWorkflowInstanceId());
        assertEquals("sample_taskId", taskResult.getTaskId());
        assertEquals("sample_reasonForIncompletion", taskResult.getReasonForIncompletion());
        assertEquals(123L, taskResult.getCallbackAfterSeconds());
        assertEquals("sample_workerId", taskResult.getWorkerId());
        assertEquals(TaskResult.Status.IN_PROGRESS, taskResult.getStatus());
        assertEquals("sample_externalOutputPayloadStoragePath", taskResult.getExternalOutputPayloadStoragePath());
        assertNull(taskResult.getSubWorkflowId());
        
        Map<String, Object> outputData = taskResult.getOutputData();
        assertNotNull(outputData);
        assertEquals(1, outputData.size());
        assertEquals("sample_value", outputData.get("sample_key"));
        
        List<TaskExecLog> logs = taskResult.getLogs();
        assertNotNull(logs);
        assertEquals(1, logs.size());
        TaskExecLog log = logs.get(0);
        assertEquals("sample_log", log.getLog());
        assertEquals(123L, log.getCreatedTime());
        assertEquals("sample_taskId", log.getTaskId());
        
        String marshalledJSON = mapper.writeValueAsString(taskResult);
        JsonNode originalNode = mapper.readTree(serverJSON);
        JsonNode marshalledNode = mapper.readTree(marshalledJSON);
        assertEquals(originalNode, marshalledNode);
    }
}