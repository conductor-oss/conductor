import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import static org.junit.jupiter.api.Assertions.*

public class TestSerDerAccessKeyStatus {

    @Test
    public void testSerializationDeserialization() throws Exception {
        String serverJSON = "{\"values\":[\"ACTIVE\",\"INACTIVE\"],\"constants\":{\"ACTIVE\":\"(0)\",\"INACTIVE\":\"(1)\"},\"sampleValue\":\"ACTIVE\"}";
        ObjectMapper mapper = new ObjectMapper();

        class AccessKeyStatusWrapper {
            public List<String> values;
            public Map<String, String> constants;
            public AccessKeyStatus sampleValue;
        }

        AccessKeyStatusWrapper wrapper = mapper.readValue(serverJSON, AccessKeyStatusWrapper.class);
        assertNotNull(wrapper);
        assertEquals(Arrays.asList("ACTIVE", "INACTIVE"), wrapper.values);
        assertEquals(Map.of("ACTIVE", "(0)", "INACTIVE", "(1)"), wrapper.constants);
        assertEquals(AccessKeyStatus.ACTIVE, wrapper.sampleValue);

        String serializedJSON = mapper.writeValueAsString(wrapper);
        JsonNode original = mapper.readTree(serverJSON);
        JsonNode serialized = mapper.readTree(serializedJSON);
        assertEquals(original, serialized);
    }
}