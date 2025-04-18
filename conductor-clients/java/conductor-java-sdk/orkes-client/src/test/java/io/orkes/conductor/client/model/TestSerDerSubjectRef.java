import com.fasterxml.jackson.databind.ObjectMapper
import io.orkes.conductor.client.model.SubjectRef
import org.junit.jupiter.api.Test
import static org.junit.jupiter.api.Assertions.*

class TestSerDerSubjectRef {
    private final ObjectMapper objectMapper = new ObjectMapper()

    @Test
    void testSerDes() throws Exception {
        String serverJson = "{\"id\":\"sample_id\",\"type\":\"sample_type\"}"
        SubjectRef subjectRef = objectMapper.readValue(serverJson, SubjectRef.class)
        assertEquals("sample_id", subjectRef.getId())
        assertNull(subjectRef.getType())
        String serializedJson = objectMapper.writeValueAsString(subjectRef)
        Object original = objectMapper.readTree(serverJson)
        Object serialized = objectMapper.readTree(serializedJson)
        assertEquals(original, serialized)
    }
}