import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.JsonNode
import org.junit.jupiter.api.Test
import static org.junit.jupiter.api.Assertions.*

public class TestSerDerSubject {

    @Test
    public void testSerDeser() throws Exception {
        String json = "{\"id\":\"sample_id\",\"type\":{\"values\":[\"USER\",\"ROLE\",\"GROUP\"],\"constants\":{\"ROLE\":\"(1)\",\"GROUP\":\"(2)\",\"USER\":\"(0)\"},\"sampleValue\":\"USER\"}}"
        ObjectMapper mapper = new ObjectMapper()
        Subject subject = mapper.readValue(json, Subject.class)
        assertEquals("sample_id", subject.getId())
        assertNull(subject.getType())
        String serializedJson = mapper.writeValueAsString(subject)
        JsonNode original = mapper.readTree(json)
        JsonNode serialized = mapper.readTree(serializedJson)
        assertEquals(original, serialized)
    }
}