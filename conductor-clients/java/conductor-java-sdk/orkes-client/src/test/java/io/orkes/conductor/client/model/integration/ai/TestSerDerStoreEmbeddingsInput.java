import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import static org.junit.jupiter.api.Assertions.*

class TestSerDerStoreEmbeddingsInput {

    @Test
    fun testSerializationDeserialization() {
        val serverJSON = """{"embeddings":[3.14],"metadata":{"sample_key":"sample_value"},"vectorDB":"sample_vectorDB","namespace":"sample_namespace","index":"sample_index","id":"sample_id"}"""
        val objectMapper = ObjectMapper()
        val input = objectMapper.readValue(serverJSON, StoreEmbeddingsInput::class.java)
        assertNotNull(input)
        assertEquals("sample_vectorDB", input.vectorDB)
        assertEquals("sample_index", input.index)
        assertEquals("sample_namespace", input.namespace)
        assertEquals("sample_id", input.id)
        assertEquals(listOf(3.14f), input.embeddings)
        assertEquals(mapOf("sample_key" to "sample_value"), input.metadata)

        val serializedJSON = objectMapper.writeValueAsString(input)
        val originalTree = objectMapper.readTree(serverJSON)
        val serializedTree = objectMapper.readTree(serializedJSON)
        assertEquals(originalTree, serializedTree)
    }
}