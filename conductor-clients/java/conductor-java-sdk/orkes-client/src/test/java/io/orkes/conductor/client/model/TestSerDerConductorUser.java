import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.orkes.conductor.client.model.ConductorUser
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.util.Arrays
import java.util.Map

public class TestSerDerConductorUser {

    @Test
    public void testSerializationDeserialization() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        String serverJson = "{\"encryptedIdDisplayValue\":\"sample_encryptedIdDisplayValue\",\"roles\":[{\"permissions\":[{\"name\":\"sample_name\"}],\"name\":\"sample_name\"}],\"encryptedId\":true,\"name\":\"sample_name\",\"groups\":[{\"roles\":[{\"permissions\":[{\"name\":\"sample_name\"}],\"name\":\"sample_name\"}],\"defaultAccess\":{\"${ResourceType}\":[{\"values\":[\"CREATE\",\"READ\",\"EXECUTE\",\"UPDATE\",\"DELETE\"],\"constants\":{\"READ\":\"(100)\",\"EXECUTE\":\"(200)\",\"DELETE\":\"(400)\",\"CREATE\":\"(0)\",\"UPDATE\":\"(300)\"},\"sampleValue\":\"CREATE\"}]},\"description\":\"sample_description\",\"id\":\"sample_id\"}],\"id\":\"sample_id\",\"uuid\":\"sample_uuid\"}";
        ConductorUser user = objectMapper.readValue(serverJson, ConductorUser.class);

        Assertions.assertNotNull(user);
        Assertions.assertEquals("sample_name", user.getName());
        Assertions.assertEquals("sample_id", user.getId());
        Assertions.assertEquals("sample_uuid", user.getUuid());
        Assertions.assertTrue(user.getEncryptedId());
        Assertions.assertEquals("sample_encryptedIdDisplayValue", user.getEncryptedIdDisplayValue());

        Assertions.assertNotNull(user.getRoles());
        Assertions.assertEquals(1, user.getRoles().size());
        var role = user.getRoles().get(0);
        Assertions.assertEquals("sample_name", role.getName());
        Assertions.assertNotNull(role.getPermissions());
        Assertions.assertEquals(1, role.getPermissions().size());
        var permission = role.getPermissions().get(0);
        Assertions.assertEquals("sample_name", permission.getName());

        Assertions.assertNotNull(user.getGroups());
        Assertions.assertEquals(1, user.getGroups().size());
        var group = user.getGroups().get(0);
        Assertions.assertEquals("sample_description", group.getDescription());
        Assertions.assertEquals("sample_id", group.getId());

        Assertions.assertNotNull(group.getRoles());
        Assertions.assertEquals(1, group.getRoles().size());
        var groupRole = group.getRoles().get(0);
        Assertions.assertEquals("sample_name", groupRole.getName());

        Assertions.assertNotNull(group.getDefaultAccess());
        Map<String, List<Access>> defaultAccess = group.getDefaultAccess();
        Assertions.assertTrue(defaultAccess.containsKey("${ResourceType}"));
        List<Access> accesses = defaultAccess.get("${ResourceType}");
        Assertions.assertEquals(1, accesses.size());
        var access = accesses.get(0);
        Assertions.assertEquals(Arrays.asList("CREATE","READ","EXECUTE","UPDATE","DELETE"), access.getValues());
        Assertions.assertEquals("(100)", access.getConstants().get("READ"));
        Assertions.assertEquals("(200)", access.getConstants().get("EXECUTE"));
        Assertions.assertEquals("(400)", access.getConstants().get("DELETE"));
        Assertions.assertEquals("(0)", access.getConstants().get("CREATE"));
        Assertions.assertEquals("(300)", access.getConstants().get("UPDATE"));
        Assertions.assertEquals("CREATE", access.getSampleValue());

        String marshalledJson = objectMapper.writeValueAsString(user);
        JsonNode originalNode = objectMapper.readTree(serverJson);
        JsonNode marshalledNode = objectMapper.readTree(marshalledJson);
        Assertions.assertEquals(originalNode, marshalledNode);
    }
}