import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import static org.junit.jupiter.api.Assertions.*
import io.orkes.conductor.client.model.integration.IntegrationApi
import io.orkes.conductor.client.model.TagObject
import java.util.*

public class TestSerDerIntegrationApi {

    @Test
    public void testSerializationDeserialization() throws Exception {
        String serverJSON = "{\"integrationName\":\"sample_integrationName\",\"configuration\":{\"${ConfigKey}\":\"sample_Object\"},\"description\":\"sample_description\",\"api\":\"sample_api\",\"enabled\":true,\"tags\":[{\"type\":\"sample_type\",\"value\":\"sample_value\",\"key\":\"sample_key\"}]}"

        ObjectMapper mapper = new ObjectMapper()

        IntegrationApi pojo = mapper.readValue(serverJSON, IntegrationApi.class)

        assertEquals("sample_integrationName", pojo.getIntegrationName())
        assertEquals("sample_api", pojo.getApi())
        assertEquals("sample_description", pojo.getDescription())
        assertTrue(pojo.getEnabled())

        assertNotNull(pojo.getConfiguration())
        assertEquals("sample_Object", pojo.getConfiguration().get("${ConfigKey}"))

        assertNotNull(pojo.getTags())
        assertEquals(1, pojo.getTags().size())
        
        TagObject tag = pojo.getTags().get(0)
        assertEquals("sample_type", tag.getType())
        assertEquals("sample_value", tag.getValue())
        assertEquals("sample_key", tag.getKey())

        String marshalledJSON = mapper.writeValueAsString(pojo)
        IntegrationApi pojo2 = mapper.readValue(marshalledJSON, IntegrationApi.class)

        assertEquals(pojo, pojo2)
    }
}