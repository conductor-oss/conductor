import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class TestSerDerPollData {

    @Test
    public void testSerializationDeserialization() throws Exception {
        String serverJSON = "{\"workerId\":\"sample_workerId\",\"lastPollTime\":123,\"queueName\":\"sample_queueName\",\"domain\":\"sample_domain\"}";
        ObjectMapper objectMapper = new ObjectMapper();
        
        // Deserialize JSON to PollData
        PollData pollData = objectMapper.readValue(serverJSON, PollData.class);
        
        // Assert fields are correctly populated
        assertEquals("sample_workerId", pollData.getWorkerId());
        assertEquals(123, pollData.getLastPollTime());
        assertEquals("sample_queueName", pollData.getQueueName());
        assertEquals("sample_domain", pollData.getDomain());
        
        // Serialize PollData back to JSON
        String serializedJSON = objectMapper.writeValueAsString(pollData);
        
        // Compare the original and serialized JSON
        JsonNode originalJsonNode = objectMapper.readTree(serverJSON);
        JsonNode serializedJsonNode = objectMapper.readTree(serializedJSON);
        assertEquals(originalJsonNode, serializedJsonNode);
    }
}