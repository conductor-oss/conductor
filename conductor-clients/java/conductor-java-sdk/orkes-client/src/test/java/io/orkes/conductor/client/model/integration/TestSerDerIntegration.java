import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import static org.junit.jupiter.api.Assertions.*
import io.orkes.conductor.client.model.integration.Integration
import io.orkes.conductor.client.model.integration.TagObject

class TestSerDerIntegration {

    private val objectMapper = ObjectMapper()

    private val serverJSON = """{
        "apis":[{
            "integrationName":"sample_integrationName",
            "configuration":{"${ConfigKey}":"sample_Object"},
            "description":"sample_description",
            "api":"sample_api",
            "enabled":true,
            "tags":[{"type":"sample_type","value":"sample_value","key":"sample_key"}]
        }],
        "configuration":{"${ConfigKey}":"sample_Object"},
        "name":"sample_name",
        "description":"sample_description",
        "modelsCount":123,
        "type":"sample_type",
        "category":{
            "values":["API","AI_MODEL","VECTOR_DB","RELATIONAL_DB","MESSAGE_BROKER","GIT","EMAIL"],
            "constants":{
                "AI_MODEL":"(1)",
                "GIT":"(5)",
                "API":"(0)",
                "RELATIONAL_DB":"(3)",
                "VECTOR_DB":"(2)",
                "EMAIL":"(6)",
                "MESSAGE_BROKER":"(4)"
            },
            "sampleValue":"API"
        },
        "enabled":true,
        "tags":[{"type":"sample_type","value":"sample_value","key":"sample_key"}]
    }"""

    @Test
    fun testSerializationDeserialization() {
        val integration = objectMapper.readValue(serverJSON, Integration::class.java)
        assertNotNull(integration)
        assertEquals("sample_name", integration.name)
        assertEquals("sample_description", integration.description)
        assertEquals(123, integration.modelsCount)
        assertEquals("sample_type", integration.type)
        assertTrue(integration.enabled)
        
        assertNotNull(integration.configuration)
        assertEquals("sample_Object", integration.configuration["\${ConfigKey}"])
        
        val category = integration.category
        assertNotNull(category)
        assertEquals("API", category.sampleValue)
        assertEquals(listOf("API","AI_MODEL","VECTOR_DB","RELATIONAL_DB","MESSAGE_BROKER","GIT","EMAIL"), category.values)
        assertEquals("(1)", category.constants["AI_MODEL"])
        assertEquals("(5)", category.constants["GIT"])
        assertEquals("(0)", category.constants["API"])
        assertEquals("(3)", category.constants["RELATIONAL_DB"])
        assertEquals("(2)", category.constants["VECTOR_DB"])
        assertEquals("(6)", category.constants["EMAIL"])
        assertEquals("(4)", category.constants["MESSAGE_BROKER"])
        
        val tags = integration.tags
        assertNotNull(tags)
        assertEquals(1, tags.size)
        val tag = tags[0]
        assertEquals("sample_type", tag.type)
        assertEquals("sample_value", tag.value)
        assertEquals("sample_key", tag.key)
        
        val apis = integration.apis
        assertNotNull(apis)
        assertEquals(1, apis.size)
        val api = apis[0]
        assertEquals("sample_integrationName", api.integrationName)
        assertEquals("sample_Object", api.configuration["\${ConfigKey}"])
        assertEquals("sample_description", api.description)
        assertEquals("sample_api", api.api)
        assertTrue(api.enabled)
        
        val apiTags = api.tags
        assertNotNull(apiTags)
        assertEquals(1, apiTags.size)
        val apiTag = apiTags[0]
        assertEquals("sample_type", apiTag.type)
        assertEquals("sample_value", apiTag.value)
        assertEquals("sample_key", apiTag.key)
        
        val serializedJSON = objectMapper.writeValueAsString(integration)
        val originalTree = objectMapper.readTree(serverJSON)
        val serializedTree = objectMapper.readTree(serializedJSON)
        assertEquals(originalTree, serializedTree)
    }
}