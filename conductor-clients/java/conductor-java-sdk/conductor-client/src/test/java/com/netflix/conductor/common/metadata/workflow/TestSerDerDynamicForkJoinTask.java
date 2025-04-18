import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.conductor.common.metadata.workflow.DynamicForkJoinTask
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions

class TestSerDerDynamicForkJoinTask {

    private val objectMapper = ObjectMapper()

    @Test
    fun testSerializationDeserialization() {
        val serverJSON = """{"input":{"sample_key":"sample_value"},"taskName":"sample_taskName","workflowName":"sample_workflowName","type":"sample_type","referenceName":"sample_referenceName"}"""
        
        // Deserialize JSON to POJO
        val task = objectMapper.readValue(serverJSON, DynamicForkJoinTask::class.java)
        
        // Assert fields are correctly populated
        Assertions.assertEquals("sample_taskName", task.taskName)
        Assertions.assertEquals("sample_workflowName", task.workflowName)
        Assertions.assertEquals("sample_referenceName", task.referenceName)
        Assertions.assertEquals("sample_type", task.type)
        Assertions.assertNotNull(task.input)
        Assertions.assertEquals(1, task.input.size)
        Assertions.assertEquals("sample_value", task.input["sample_key"])
        
        // Serialize POJO back to JSON
        val serializedJSON = objectMapper.writeValueAsString(task)
        
        // Compare the original and serialized JSON as JsonNodes
        val originalNode = objectMapper.readTree(serverJSON)
        val serializedNode = objectMapper.readTree(serializedJSON)
        Assertions.assertEquals(originalNode, serializedNode)
    }
}