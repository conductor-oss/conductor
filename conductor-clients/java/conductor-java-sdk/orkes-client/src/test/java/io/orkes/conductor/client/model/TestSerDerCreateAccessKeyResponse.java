import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class TestSerDerCreateAccessKeyResponse {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testSerDer() throws Exception {
        String serverJSON = "{\"id\":\"sample_id\",\"secret\":\"sample_secret\"}";
        CreateAccessKeyResponse response = objectMapper.readValue(serverJSON, CreateAccessKeyResponse.class);
        assertEquals("sample_id", response.getId());
        assertEquals("sample_secret", response.getSecret());
        String serializedJSON = objectMapper.writeValueAsString(response);
        assertEquals(objectMapper.readTree(serverJSON), objectMapper.readTree(serializedJSON));
    }
}