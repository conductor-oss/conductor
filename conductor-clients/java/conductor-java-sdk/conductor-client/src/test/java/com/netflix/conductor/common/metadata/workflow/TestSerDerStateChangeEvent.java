import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.conductor.common.metadata.workflow.StateChangeEvent
import org.junit.jupiter.api.Test
import static org.junit.jupiter.api.Assertions.assertEquals

class TestSerDerStateChangeEvent {

    private val objectMapper = ObjectMapper()
    private val serverJson = """{"payload":{"key":"sample_value"},"type":"sample_type"}"""

    @Test
    fun testSerializationDeserialization() {
        // Unmarshal serverJSON to SDK POJO
        val event = objectMapper.readValue(serverJson, StateChangeEvent::class.java)

        // Assert fields are correctly populated
        assertEquals("sample_type", event.type)
        assertEquals(1, event.payload.size)
        assertEquals("sample_value", event.payload["key"])

        // Marshal the POJO back to JSON
        val marshalledJson = objectMapper.writeValueAsString(event)

        // Compare the JSONs
        val originalJsonMap = objectMapper.readTree(serverJson)
        val marshalledJsonMap = objectMapper.readTree(marshalledJson)
        assertEquals(originalJsonMap, marshalledJsonMap)
    }
}