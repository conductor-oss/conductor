import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.core.type.TypeReference
import org.junit.jupiter.api.Test
import static org.junit.jupiter.api.Assertions.*

public class TestSerDerChatCompletion {

    private static final String SERVER_JSON = "{\"instructions\":\"sample_instructions\",\"jsonOutput\":true,\"messages\":[{\"role\":\"sample_role\",\"message\":\"sample_message\"}]}"

    @Test
    public void testSerializationDeserialization() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper()
        ChatCompletion chatCompletion = objectMapper.readValue(SERVER_JSON, ChatCompletion.class)
        assertEquals("sample_instructions", chatCompletion.getInstructions())
        assertTrue(chatCompletion.isJsonOutput())
        assertNotNull(chatCompletion.getMessages())
        assertEquals(1, chatCompletion.getMessages().size())
        ChatMessage message = chatCompletion.getMessages().get(0)
        assertEquals("sample_role", message.getRole())
        assertEquals("sample_message", message.getMessage())

        String serializedJson = objectMapper.writeValueAsString(chatCompletion)
        assertEquals(objectMapper.readTree(SERVER_JSON), objectMapper.readTree(serializedJson))
    }
}