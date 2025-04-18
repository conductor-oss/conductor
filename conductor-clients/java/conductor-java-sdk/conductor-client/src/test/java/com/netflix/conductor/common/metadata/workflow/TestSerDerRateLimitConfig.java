import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.JsonNode
import com.netflix.conductor.common.metadata.workflow.RateLimitConfig
import org.junit.jupiter.api.Test
import static org.junit.jupiter.api.Assertions.*

public class TestSerDerRateLimitConfig {

    @Test
    public void testSerializationDeserialization() throws Exception {
        String serverJSON = "{\"concurrentExecLimit\":123,\"rateLimitKey\":\"sample_rateLimitKey\"}"
        ObjectMapper objectMapper = new ObjectMapper()
        RateLimitConfig config = objectMapper.readValue(serverJSON, RateLimitConfig.class)
        assertEquals(123, config.getConcurrentExecLimit())
        assertEquals("sample_rateLimitKey", config.getRateLimitKey())
        String serializedJSON = objectMapper.writeValueAsString(config)
        JsonNode originalNode = objectMapper.readTree(serverJSON)
        JsonNode serializedNode = objectMapper.readTree(serializedJSON)
        assertEquals(originalNode, serializedNode)
    }
}