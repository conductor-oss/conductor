import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import static org.junit.jupiter.api.Assertions.*

public class TestSerDerBulkResponse {
    @Test
    public void testSerializationDeserialization() throws Exception {
        String serverJSON = "{\"bulkErrorResults\":{\"sample_key\":\"sample_value\"},\"bulkSuccessfulResults\":[{\"value\":\"sample_string_value\"}],\"message\":\"sample_message\"}";
        ObjectMapper mapper = new ObjectMapper();
        
        BulkResponse<SuccessResult> response = mapper.readValue(serverJSON, new TypeReference<BulkResponse<SuccessResult>>(){});
        
        assertNotNull(response);
        assertEquals("sample_message", response.getMessage());
        
        assertNotNull(response.getBulkErrorResults());
        assertEquals(1, response.getBulkErrorResults().size());
        assertEquals("sample_value", response.getBulkErrorResults().get("sample_key"));
        
        assertNotNull(response.getBulkSuccessfulResults());
        assertEquals(1, response.getBulkSuccessfulResults().size());
        assertEquals("sample_string_value", response.getBulkSuccessfulResults().get(0).getValue());
        
        String serializedJSON = mapper.writeValueAsString(response);
        ObjectMapper mapperCanonical = new ObjectMapper();
        JsonNode tree1 = mapperCanonical.readTree(serverJSON);
        JsonNode tree2 = mapperCanonical.readTree(serializedJSON);
        assertEquals(tree1, tree2);
    }
    
    public static class SuccessResult {
        private String value;
        
        public SuccessResult() {}
        
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
    }
}