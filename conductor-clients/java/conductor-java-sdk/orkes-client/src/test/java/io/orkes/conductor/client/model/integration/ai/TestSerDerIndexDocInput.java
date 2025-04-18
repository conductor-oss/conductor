public class TestSerDerIndexDocInput {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final String serverJSON = "{\"metadata\":{\"sample_key\":\"sample_value\"},\"chunkSize\":123,\"chunkOverlap\":123,\"llmProvider\":\"sample_llmProvider\",\"docId\":\"sample_docId\",\"vectorDB\":\"sample_vectorDB\",\"index\":\"sample_index\",\"mediaType\":\"sample_mediaType\",\"url\":\"sample_url\",\"namespace\":\"sample_namespace\",\"embeddingModelProvider\":\"sample_embeddingModelProvider\",\"model\":\"sample_model\",\"text\":\"sample_text\",\"embeddingModel\":\"sample_embeddingModel\",\"dimensions\":123}";

    @Test
    public void testSerializationDeserialization() throws Exception {
        // Unmarshal JSON to POJO
        IndexDocInput obj = objectMapper.readValue(serverJSON, IndexDocInput.class);

        // Assert fields
        assertEquals("sample_llmProvider", obj.getLlmProvider());
        assertEquals("sample_model", obj.getModel());
        assertEquals("sample_embeddingModelProvider", obj.getEmbeddingModelProvider());
        assertEquals("sample_embeddingModel", obj.getEmbeddingModel());
        assertEquals("sample_vectorDB", obj.getVectorDB());
        assertEquals("sample_text", obj.getText());
        assertEquals("sample_docId", obj.getDocId());
        assertEquals("sample_url", obj.getUrl());
        assertEquals("sample_mediaType", obj.getMediaType());
        assertEquals("sample_namespace", obj.getNamespace());
        assertEquals("sample_index", obj.getIndex());
        assertEquals(123, obj.getChunkSize());
        assertEquals(123, obj.getChunkOverlap());
        assertEquals(123, obj.getDimensions());

        // Assert metadata map
        assertNotNull(obj.getMetadata());
        assertEquals(1, obj.getMetadata().size());
        assertTrue(obj.getMetadata().containsKey("sample_key"));
        assertEquals("sample_value", obj.getMetadata().get("sample_key"));

        // Marshal POJO back to JSON
        String serializedJSON = objectMapper.writeValueAsString(obj);

        // Compare original and serialized JSON
        JsonNode originalNode = objectMapper.readTree(serverJSON);
        JsonNode serializedNode = objectMapper.readTree(serializedJSON);
        assertEquals(originalNode, serializedNode);
    }
}