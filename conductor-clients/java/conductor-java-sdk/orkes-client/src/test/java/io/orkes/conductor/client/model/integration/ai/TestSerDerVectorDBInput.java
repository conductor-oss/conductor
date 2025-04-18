package io.orkes.conductor.client.model.integration.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class TestSerDerVectorDBInput {

    @Test
    public void testSerializationDeserialization() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        String serverJSON = "{\"embeddings\":[3.14],\"metadata\":{\"sample_key\":\"sample_value\"},\"vectorDB\":\"sample_vectorDB\",\"query\":\"sample_query\",\"namespace\":\"sample_namespace\",\"index\":\"sample_index\",\"dimensions\":123}";
        VectorDBInput pojo = objectMapper.readValue(serverJSON, VectorDBInput.class);
        
        assertEquals("sample_vectorDB", pojo.getVectorDB());
        assertEquals("sample_query", pojo.getQuery());
        assertEquals("sample_namespace", pojo.getNamespace());
        assertEquals("sample_index", pojo.getIndex());
        assertEquals(123, pojo.getDimensions());

        assertNotNull(pojo.getEmbeddings());
        assertEquals(1, pojo.getEmbeddings().size());
        assertEquals(3.14f, pojo.getEmbeddings().get(0));

        assertNotNull(pojo.getMetadata());
        assertEquals("sample_value", pojo.getMetadata().get("sample_key"));

        String serializedJSON = objectMapper.writeValueAsString(pojo);
        JsonNode original = objectMapper.readTree(serverJSON);
        JsonNode serialized = objectMapper.readTree(serializedJSON);
        assertEquals(original, serialized);
    }
}