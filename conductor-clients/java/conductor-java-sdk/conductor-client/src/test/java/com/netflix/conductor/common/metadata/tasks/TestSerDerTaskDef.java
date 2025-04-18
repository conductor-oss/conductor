import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskDef.RetryLogic;
import com.netflix.conductor.common.metadata.tasks.TaskDef.TimeoutPolicy;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.HashMap;

public class TestSerDerTaskDef {

    @Test
    public void testSerializationDeserialization() throws Exception {
        String serverJSON = "{\"timeoutPolicy\":{\"values\":[\"RETRY\",\"TIME_OUT_WF\",\"ALERT_ONLY\"],\"constants\":{\"ALERT_ONLY\":\"(2)\",\"TIME_OUT_WF\":\"(1)\",\"RETRY\":\"(0)\"},\"sampleValue\":\"RETRY\"},\"inputKeys\":[\"sample_inputKeys\"],\"concurrentExecLimit\":123,\"isolationGroupId\":\"sample_isolationGroupId\",\"retryCount\":123,\"description\":\"sample_description\",\"inputTemplate\":{\"sample_key\":\"sample_value\"},\"ownerEmail\":\"sample_ownerEmail\",\"baseType\":\"sample_baseType\",\"totalTimeoutSeconds\":123,\"retryDelaySeconds\":123,\"backoffScaleFactor\":123,\"rateLimitPerFrequency\":123,\"retryLogic\":{\"values\":[\"FIXED\",\"EXPONENTIAL_BACKOFF\",\"LINEAR_BACKOFF\"],\"constants\":{\"EXPONENTIAL_BACKOFF\":\"(1)\",\"FIXED\":\"(0)\",\"LINEAR_BACKOFF\":\"(2)\"},\"sampleValue\":\"FIXED\"},\"responseTimeoutSeconds\":123,\"name\":\"sample_name\",\"timeoutSeconds\":123,\"rateLimitFrequencyInSeconds\":123,\"outputKeys\":[\"sample_outputKeys\"],\"executionNameSpace\":\"sample_executionNameSpace\",\"pollTimeoutSeconds\":123}";

        ObjectMapper objectMapper = new ObjectMapper();
        TaskDef taskDef = objectMapper.readValue(serverJSON, TaskDef.class);

        Assertions.assertEquals("sample_name", taskDef.getName());
        Assertions.assertEquals("sample_description", taskDef.getDescription());
        Assertions.assertEquals(123, taskDef.getRetryCount());
        Assertions.assertEquals(123L, taskDef.getTimeoutSeconds());
        Assertions.assertEquals(123, taskDef.getTotalTimeoutSeconds());
        Assertions.assertEquals(123, taskDef.getRetryDelaySeconds());
        Assertions.assertEquals(123, taskDef.getBackoffScaleFactor());
        Assertions.assertEquals(123, taskDef.getRateLimitPerFrequency());
        Assertions.assertEquals(123, taskDef.getRateLimitFrequencyInSeconds());
        Assertions.assertEquals(123L, taskDef.getResponseTimeoutSeconds());
        Assertions.assertEquals("sample_isolationGroupId", taskDef.getIsolationGroupId());
        Assertions.assertEquals("sample_executionNameSpace", taskDef.getExecutionNameSpace());
        Assertions.assertEquals("sample_ownerEmail", taskDef.getOwnerEmail());
        Assertions.assertEquals("sample_baseType", taskDef.getBaseType());
        Assertions.assertEquals(123, taskDef.getConcurrentExecLimit());
        Assertions.assertEquals(123, taskDef.getPollTimeoutSeconds());

        Assertions.assertEquals(Arrays.asList("sample_inputKeys"), taskDef.getInputKeys());
        Assertions.assertEquals(Arrays.asList("sample_outputKeys"), taskDef.getOutputKeys());

        Assertions.assertEquals("sample_value", taskDef.getInputTemplate().get("sample_key"));

        Assertions.assertEquals(TimeoutPolicy.RETRY, taskDef.getTimeoutPolicy());
        Assertions.assertEquals(RetryLogic.FIXED, taskDef.getRetryLogic());

        String serializedJSON = objectMapper.writeValueAsString(taskDef);
        JsonNode original = objectMapper.readTree(serverJSON);
        JsonNode serialized = objectMapper.readTree(serializedJSON);
        Assertions.assertEquals(original, serialized);
    }
}