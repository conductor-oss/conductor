public class TestSerDerGenerateTokenRequest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testSerializationDeserialization() throws Exception {
        String serverJSON = "{\"keyId\":\"sample_keyId\",\"keySecret\":\"sample_keySecret\"}";
        GenerateTokenRequest request = objectMapper.readValue(serverJSON, GenerateTokenRequest.class);
        Assertions.assertEquals("sample_keyId", request.getKeyId());
        Assertions.assertEquals("sample_keySecret", request.getKeySecret());

        String marshalledJSON = objectMapper.writeValueAsString(request);
        JsonNode original = objectMapper.readTree(serverJSON);
        JsonNode marshalled = objectMapper.readTree(marshalledJSON);
        Assertions.assertEquals(original, marshalled);
    }
}