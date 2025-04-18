import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import io.orkes.conductor.client.model.ConductorApplication;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class TestSerDerConductorApplication {
    @Test
    public void testSerializationDeserialization() throws Exception {
        String serverJSON = "{\"updatedBy\":\"sample_updatedBy\",\"createdBy\":\"sample_createdBy\",\"createTime\":123,\"name\":\"sample_name\",\"updateTime\":123,\"id\":\"sample_id\"}";

        ObjectMapper objectMapper = new ObjectMapper();
        ConductorApplication app = objectMapper.readValue(serverJSON, ConductorApplication.class);

        assertEquals("sample_createdBy", app.getCreatedBy());
        assertEquals("sample_id", app.getId());
        assertEquals("sample_name", app.getName());

        String serializedJSON = objectMapper.writeValueAsString(app);

        JsonNode originalJson = objectMapper.readTree(serverJSON);
        JsonNode serializedJson = objectMapper.readTree(serializedJSON);

        assertEquals(originalJson, serializedJson);
    }
}