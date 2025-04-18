import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.JsonNode
import io.orkes.conductor.client.model.integration.Category
import org.junit.jupiter.api.Test
import static org.junit.jupiter.api.Assertions.*
import java.util.Arrays
import java.util.Map

public class TestSerDerCategory {
    @Test
    public void testSerializationDeserialization() throws Exception {
        String serverJSON = "{\"values\":[\"API\",\"AI_MODEL\",\"VECTOR_DB\",\"RELATIONAL_DB\",\"MESSAGE_BROKER\",\"GIT\",\"EMAIL\"],\"constants\":{\"AI_MODEL\":\"(1)\",\"GIT\":\"(5)\",\"API\":\"(0)\",\"RELATIONAL_DB\":\"(3)\",\"VECTOR_DB\":\"(2)\",\"EMAIL\":\"(6)\",\"MESSAGE_BROKER\":\"(4)\"},\"sampleValue\":\"API\"}"
        
        ObjectMapper mapper = new ObjectMapper()
        CategoryPOJO pojo = mapper.readValue(serverJSON, CategoryPOJO.class)
        
        assertNotNull(pojo)
        assertEquals(Arrays.asList("API","AI_MODEL","VECTOR_DB","RELATIONAL_DB","MESSAGE_BROKER","GIT","EMAIL"), pojo.getValues())
        
        Map<String, String> expectedConstants = Map.of(
            "AI_MODEL", "(1)",
            "GIT", "(5)",
            "API", "(0)",
            "RELATIONAL_DB", "(3)",
            "VECTOR_DB", "(2)",
            "EMAIL", "(6)",
            "MESSAGE_BROKER", "(4)"
        )
        assertEquals(expectedConstants, pojo.getConstants())
        
        assertEquals(Category.API, pojo.getSampleValue())
        
        String serializedJSON = mapper.writeValueAsString(pojo)
        JsonNode original = mapper.readTree(serverJSON)
        JsonNode serialized = mapper.readTree(serializedJSON)
        assertEquals(original, serialized)
    }
}

class CategoryPOJO {
    private List<String> values
    private Map<String, String> constants
    private Category sampleValue

    public List<String> getValues() {
        return values
    }

    public void setValues(List<String> values) {
        this.values = values
    }

    public Map<String, String> getConstants() {
        return constants
    }

    public void setConstants(Map<String, String> constants) {
        this.constants = constants
    }

    public Category getSampleValue() {
        return sampleValue
    }

    public void setSampleValue(Category sampleValue) {
        this.sampleValue = sampleValue
    }
}