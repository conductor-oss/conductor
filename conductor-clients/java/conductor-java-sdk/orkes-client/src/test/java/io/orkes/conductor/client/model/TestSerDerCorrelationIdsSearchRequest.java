import com.fasterxml.jackson.databind.ObjectMapper;
import io.orkes.conductor.client.model.CorrelationIdsSearchRequest;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class TestSerDerCorrelationIdsSearchRequest {

    @Test
    public void testSerializationDeserialization() throws Exception {
        String serverJSON = "{\"workflowNames\":[\"sample_workflowNames\"],\"correlationIds\":[\"sample_correlationIds\"]}";
        ObjectMapper objectMapper = new ObjectMapper();
        
        CorrelationIdsSearchRequest request = objectMapper.readValue(serverJSON, CorrelationIdsSearchRequest.class);
        assertNotNull(request);
        assertEquals(List.of("sample_correlationIds"), request.getCorrelationIds());
        assertEquals(List.of("sample_workflowNames"), request.getWorkflowNames());
        
        String serializedJSON = objectMapper.writeValueAsString(request);
        assertEquals(serverJSON, serializedJSON);
    }
}