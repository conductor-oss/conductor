import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.conductor.common.run.SearchResult
import org.junit.jupiter.api.Test
import static org.junit.jupiter.api.Assertions.*
import java.util.List

public class TestSerDerSearchResult {
    private static final String SERVER_JSON = "{\"totalHits\":123,\"results\":[{\"value\":\"sample_string_value\"}]}"
    private ObjectMapper objectMapper = new ObjectMapper()

    public static class Result {
        private String value

        public Result() {}

        public String getValue() {
            return value
        }

        public void setValue(String value) {
            this.value = value
        }
    }

    @Test
    public void testSerializationDeserialization() throws Exception {
        SearchResult<Result> searchResult = objectMapper.readValue(SERVER_JSON, objectMapper.getTypeFactory().constructParametricType(SearchResult.class, Result.class))

        assertEquals(123, searchResult.getTotalHits())
        assertNotNull(searchResult.getResults())
        assertEquals(1, searchResult.getResults().size())
        assertEquals("sample_string_value", searchResult.getResults().get(0).getValue())

        String serializedJson = objectMapper.writeValueAsString(searchResult)
        assertEquals(objectMapper.readTree(SERVER_JSON), objectMapper.readTree(serializedJson))
    }
}