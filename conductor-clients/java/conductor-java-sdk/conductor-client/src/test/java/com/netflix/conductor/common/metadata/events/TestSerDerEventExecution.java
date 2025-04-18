import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.conductor.common.metadata.events.EventExecution
import com.netflix.conductor.common.metadata.events.EventHandler.Action
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class TestSerDerEventExecution {

    @Test
    fun testSerializationDeserialization() {
        val serverJSON = """{"output":{"sample_key":"sample_value"},"created":123,"name":"sample_name","messageId":"sample_messageId","action":{"values":["start_workflow","complete_task","fail_task","terminate_workflow","update_workflow_variables"],"constants":{"terminate_workflow":"(3)","fail_task":"(2)","start_workflow":"(0)","complete_task":"(1)","update_workflow_variables":"(4)"},"sampleValue":"start_workflow"},"id":"sample_id","event":"sample_event","status":{"values":["IN_PROGRESS","COMPLETED","FAILED","SKIPPED"],"constants":{"IN_PROGRESS":"(0)","COMPLETED":"(1)","FAILED":"(2)","SKIPPED":"(3)"},"sampleValue":"IN_PROGRESS"}}"""
        val objectMapper = ObjectMapper()

        val eventExecution = objectMapper.readValue(serverJSON, EventExecution::class.java)

        Assertions.assertEquals("sample_id", eventExecution.id)
        Assertions.assertEquals("sample_messageId", eventExecution.messageId)
        Assertions.assertEquals("sample_name", eventExecution.name)
        Assertions.assertEquals("sample_event", eventExecution.event)
        Assertions.assertEquals(123L, eventExecution.created)
        Assertions.assertEquals(EventExecution.Status.IN_PROGRESS, eventExecution.status)
        Assertions.assertEquals(Action.Type.start_workflow, eventExecution.action)
        Assertions.assertNotNull(eventExecution.output)
        Assertions.assertEquals("sample_value", eventExecution.output["sample_key"])

        val serializedJSON = objectMapper.writeValueAsString(eventExecution)
        val originalJsonNode = objectMapper.readTree(serverJSON)
        val serializedJsonNode = objectMapper.readTree(serializedJSON)

        Assertions.assertEquals(originalJsonNode, serializedJsonNode)
    }
}