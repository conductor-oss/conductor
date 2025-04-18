import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.core.JsonProcessingException
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import com.netflix.conductor.common.metadata.workflow.DynamicForkJoinTaskList
import com.netflix.conductor.common.metadata.workflow.DynamicForkJoinTask
import java.util.Map
import java.util.HashMap
import java.util.List

public class TestSerDerDynamicForkJoinTaskList {

    private static final String SERVER_JSON = "{\"dynamicTasks\":[{\"input\":{\"sample_key\":\"sample_value\"},\"taskName\":\"sample_taskName\",\"workflowName\":\"sample_workflowName\",\"type\":\"sample_type\",\"referenceName\":\"sample_referenceName\"}]}";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testSerializationDeserialization() throws JsonProcessingException {
        // Unmarshal serverJSON to SDK POJO
        DynamicForkJoinTaskList taskList = objectMapper.readValue(SERVER_JSON, DynamicForkJoinTaskList.class);
        
        // Assert that the fields are all correctly populated
        Assertions.assertNotNull(taskList);
        List<DynamicForkJoinTask> dynamicTasks = taskList.getDynamicTasks();
        Assertions.assertNotNull(dynamicTasks);
        Assertions.assertEquals(1, dynamicTasks.size());

        DynamicForkJoinTask task = dynamicTasks.get(0);
        Assertions.assertEquals("sample_taskName", task.getTaskName());
        Assertions.assertEquals("sample_workflowName", task.getWorkflowName());
        Assertions.assertEquals("sample_type", task.getType());
        Assertions.assertEquals("sample_referenceName", task.getReferenceName());
        Map<String, Object> input = task.getInput();
        Assertions.assertNotNull(input);
        Assertions.assertEquals(1, input.size());
        Assertions.assertEquals("sample_value", input.get("sample_key"));
        
        // Marshall the POJO to JSON again
        String marshalledJson = objectMapper.writeValueAsString(taskList);
        
        // Compare the JSONs
        DynamicForkJoinTaskList reserializedTaskList = objectMapper.readValue(marshalledJson, DynamicForkJoinTaskList.class);
        Assertions.assertEquals(taskList.getDynamicTasks().size(), reserializedTaskList.getDynamicTasks().size());
        Assertions.assertEquals(taskList.getDynamicTasks().get(0).getTaskName(), reserializedTaskList.getDynamicTasks().get(0).getTaskName());
        Assertions.assertEquals(taskList.getDynamicTasks().get(0).getWorkflowName(), reserializedTaskList.getDynamicTasks().get(0).getWorkflowName());
        Assertions.assertEquals(taskList.getDynamicTasks().get(0).getType(), reserializedTaskList.getDynamicTasks().get(0).getType());
        Assertions.assertEquals(taskList.getDynamicTasks().get(0).getReferenceName(), reserializedTaskList.getDynamicTasks().get(0).getReferenceName());
        Assertions.assertEquals(taskList.getDynamicTasks().get(0).getInput(), reserializedTaskList.getDynamicTasks().get(0).getInput());
    }
}