import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;
import java.util.Map;

public class TestSerDerIdempotencyStrategy {

    @Test
    public void testSerializationDeserialization() throws Exception {
        String serverJSON = "{\"values\":[\"FAIL\",\"RETURN_EXISTING\",\"FAIL_ON_RUNNING\"],\"constants\":{\"FAIL_ON_RUNNING\":\"(2)\",\"RETURN_EXISTING\":\"(1)\",\"FAIL\":\"(0)\"},\"sampleValue\":\"FAIL\"}";
        ObjectMapper mapper = new ObjectMapper();
        
        // Deserialize JSON to POJO
        IdempotencyStrategyPOJO pojo = mapper.readValue(serverJSON, IdempotencyStrategyPOJO.class);
        
        // Assert fields
        assertNotNull(pojo);
        assertEquals(Arrays.asList("FAIL", "RETURN_EXISTING", "FAIL_ON_RUNNING"), pojo.getValues());
        assertEquals(Map.of(
            "FAIL_ON_RUNNING", "(2)",
            "RETURN_EXISTING", "(1)",
            "FAIL", "(0)"
        ), pojo.getConstants());
        assertEquals(IdempotencyStrategy.FAIL, pojo.getSampleValue());
        
        // Serialize POJO back to JSON
        String serializedJSON = mapper.writeValueAsString(pojo);
        
        // Compare JSON structures
        assertEquals(mapper.readTree(serverJSON), mapper.readTree(serializedJSON));
    }
}

class IdempotencyStrategyPOJO {
    private java.util.List<String> values;
    private Map<String, String> constants;
    private IdempotencyStrategy sampleValue;

    public java.util.List<String> getValues() {
        return values;
    }

    public void setValues(java.util.List<String> values) {
        this.values = values;
    }

    public Map<String, String> getConstants() {
        return constants;
    }

    public void setConstants(Map<String, String> constants) {
        this.constants = constants;
    }

    public IdempotencyStrategy getSampleValue() {
        return sampleValue;
    }

    public void setSampleValue(IdempotencyStrategy sampleValue) {
        this.sampleValue = sampleValue;
    }
}

enum IdempotencyStrategy {
    FAIL, RETURN_EXISTING
}