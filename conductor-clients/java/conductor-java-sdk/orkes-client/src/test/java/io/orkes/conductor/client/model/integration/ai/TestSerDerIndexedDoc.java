import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class TestSerDerIndexedDoc {

    @Test
    public void testSerializationDeserialization() throws Exception {
        String serverJSON = "{\"score\":123.456,\"metadata\":{\"sample_key\":\"sample_value\"},\"docId\":\"sample_docId\",\"text\":\"sample_text\",\"parentDocId\":\"sample_parentDocId\"}";
        ObjectMapper objectMapper = new ObjectMapper();
        
        IndexedDoc indexedDoc = objectMapper.readValue(serverJSON, IndexedDoc.class);
        
        assertEquals("sample_docId", indexedDoc.getDocId());
        assertEquals("sample_parentDocId", indexedDoc.getParentDocId());
        assertEquals("sample_text", indexedDoc.getText());
        assertEquals(123.456, indexedDoc.getScore(), 0.0001);
        assertNotNull(indexedDoc.getMetadata());
        assertEquals("sample_value", indexedDoc.getMetadata().get("sample_key"));
        
        String serializedJSON = objectMapper.writeValueAsString(indexedDoc);
        assertEquals(objectMapper.readTree(serverJSON), objectMapper.readTree(serializedJSON));
    }
}