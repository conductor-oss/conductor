import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class TestSerDerTargetRef {

    @Test
    public void testSerializationDeserialization() throws Exception {
        String serverJSON = "{\"id\":\"sample_id\",\"type\":{\"values\":[\"WORKFLOW\",\"WORKFLOW_DEF\",\"WORKFLOW_SCHEDULE\",\"EVENT_HANDLER\",\"TASK_DEF\",\"TASK_REF_NAME\",\"TASK_ID\",\"APPLICATION\",\"USER\",\"SECRET_NAME\",\"ENV_VARIABLE\",\"TAG\",\"DOMAIN\",\"INTEGRATION_PROVIDER\",\"INTEGRATION\",\"PROMPT\",\"USER_FORM_TEMPLATE\",\"SCHEMA\",\"CLUSTER_CONFIG\",\"WEBHOOK\"],\"constants\":{\"DOMAIN\":\"(12)\",\"SECRET_NAME\":\"(9)\",\"ENV_VARIABLE\":\"(10)\",\"CLUSTER_CONFIG\":\"(18)\",\"APPLICATION\":\"(7)\",\"PROMPT\":\"(15)\",\"TASK_DEF\":\"(4)\",\"USER\":\"(8)\",\"USER_FORM_TEMPLATE\":\"(16)\",\"INTEGRATION_PROVIDER\":\"(13)\",\"WORKFLOW_DEF\":\"(1)\",\"INTEGRATION\":\"(14)\",\"SCHEMA\":\"(17)\",\"WEBHOOK\":\"(19)\",\"EVENT_HANDLER\":\"(3)\",\"TASK_REF_NAME\":\"(5)\",\"TAG\":\"(11)\",\"WORKFLOW_SCHEDULE\":\"(2)\",\"WORKFLOW\":\"(0)\",\"TASK_ID\":\"(6)\"},\"sampleValue\":\"WORKFLOW\"}}";

        ObjectMapper objectMapper = new ObjectMapper();
        TargetRef targetRef = objectMapper.readValue(serverJSON, TargetRef.class);

        assertEquals("sample_id", targetRef.getId());
        assertEquals(TargetRef.TypeEnum.WORKFLOW, targetRef.getType());

        String serializedJSON = objectMapper.writeValueAsString(targetRef);
        JsonNode originalType = objectMapper.readTree(serverJSON).get("type").get("sampleValue");
        JsonNode serializedType = objectMapper.readTree(serializedJSON).get("type");

        assertEquals(originalType.asText(), serializedType.asText());
    }
}