public class TestSerDerChatMessage {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testSerializationDeserialization() throws Exception {
        String serverJSON = "{\"role\":\"sample_role\",\"message\":\"sample_message\"}";

        // Unmarshal to POJO
        ChatMessage chatMessage = objectMapper.readValue(serverJSON, ChatMessage.class);

        // Assert fields
        assertEquals("sample_role", chatMessage.getRole());
        assertEquals("sample_message", chatMessage.getMessage());

        // Marshall back to JSON
        String outputJSON = objectMapper.writeValueAsString(chatMessage);

        // Compare JSONs
        JsonNode expected = objectMapper.readTree(serverJSON);
        JsonNode actual = objectMapper.readTree(outputJSON);
        assertEquals(expected, actual);
    }
}