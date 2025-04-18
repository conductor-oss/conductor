import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.JsonNode
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import io.orkes.conductor.client.model.integration.PromptTemplateTestRequest

class TestSerDerPromptTemplateTestRequest {

    private val objectMapper = ObjectMapper()

    @Test
    fun testSerializationDeserialization() {
        val serverJson = """{"promptVariables":{"key":"sample_value"},"stopWords":["sample_stopWords"],"llmProvider":"sample_llmProvider","temperature":123.456,"model":"sample_model","prompt":"sample_prompt","topP":123.456}"""

        // Deserialize JSON to POJO
        val request = objectMapper.readValue(serverJson, PromptTemplateTestRequest::class.java)

        // Assertions
        Assertions.assertEquals("sample_llmProvider", request.llmProvider)
        Assertions.assertEquals("sample_model", request.model)
        Assertions.assertEquals("sample_prompt", request.prompt)
        Assertions.assertNotNull(request.promptVariables)
        Assertions.assertEquals("sample_value", request.promptVariables["key"])
        Assertions.assertNotNull(request.stopWords)
        Assertions.assertEquals(1, request.stopWords.size)
        Assertions.assertEquals("sample_stopWords", request.stopWords[0])
        Assertions.assertEquals(123.456, request.temperature)
        Assertions.assertEquals(123.456, request.topP)

        // Serialize POJO back to JSON
        val serializedJson = objectMapper.writeValueAsString(request)

        // Compare the original and serialized JSON
        val originalJsonNode: JsonNode = objectMapper.readTree(serverJson)
        val serializedJsonNode: JsonNode = objectMapper.readTree(serializedJson)

        Assertions.assertEquals(originalJsonNode, serializedJsonNode)
    }
}