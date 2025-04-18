import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import static org.junit.jupiter.api.Assertions.*

public class TestSerDerPermission {

    private static final ObjectMapper objectMapper = new ObjectMapper()
    private static final String serverJSON = "{\"name\":\"sample_name\"}"

    @Test
    public void testSerializationDeserialization() throws Exception {
        // Unmarshal serverJSON to POJO
        Permission permission = objectMapper.readValue(serverJSON, Permission.class)
        
        // Assert fields are correctly populated
        assertNotNull(permission)
        assertEquals("sample_name", permission.getName())
        
        // Marshal POJO back to JSON
        String serializedJSON = objectMapper.writeValueAsString(permission)
        
        // Compare the JSONs
        assertEquals(objectMapper.readTree(serverJSON), objectMapper.readTree(serializedJSON))
    }
}