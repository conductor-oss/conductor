package io.orkes.conductor.client.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;

public class TestSerDerGroup {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testSerializationDeserialization() throws Exception {
        String serverJSON = "{\"roles\":[{\"permissions\":[{\"name\":\"sample_name\"}],\"name\":\"sample_name\"}],\"defaultAccess\":{\"${ResourceType}\":[{\"values\":[\"CREATE\",\"READ\",\"EXECUTE\",\"UPDATE\",\"DELETE\"],\"constants\":{\"READ\":\"(100)\",\"EXECUTE\":\"(200)\",\"DELETE\":\"(400)\",\"CREATE\":\"(0)\",\"UPDATE\":\"(300)\"},\"sampleValue\":\"CREATE\"}]},\"description\":\"sample_description\",\"id\":\"sample_id\"}";

        Group group = objectMapper.readValue(serverJSON, Group.class);
        assertNotNull(group);
        assertEquals("sample_description", group.getDescription());
        assertEquals("sample_id", group.getId());

        List<Role> roles = group.getRoles();
        assertNotNull(roles);
        assertEquals(1, roles.size());

        Role role = roles.get(0);
        assertEquals("sample_name", role.getName());
        List<Permission> permissions = role.getPermissions();
        assertNotNull(permissions);
        assertEquals(1, permissions.size());

        Permission permission = permissions.get(0);
        assertEquals("sample_name", permission.getName());

        Map<String, List<DefaultAccessItem>> defaultAccess = group.getDefaultAccess();
        assertNotNull(defaultAccess);
        List<DefaultAccessItem> accessItems = defaultAccess.get("${ResourceType}");
        assertNotNull(accessItems);
        assertEquals(1, accessItems.size());

        DefaultAccessItem accessItem = accessItems.get(0);
        List<String> values = accessItem.getValues();
        assertNotNull(values);
        assertIterableEquals(Arrays.asList("CREATE","READ","EXECUTE","UPDATE","DELETE"), values);

        Map<String, String> constants = accessItem.getConstants();
        assertNotNull(constants);
        assertEquals("(100)", constants.get("READ"));
        assertEquals("(200)", constants.get("EXECUTE"));
        assertEquals("(400)", constants.get("DELETE"));
        assertEquals("(0)", constants.get("CREATE"));
        assertEquals("(300)", constants.get("UPDATE"));

        assertEquals(Group.InnerEnum.CREATE, Group.InnerEnum.fromValue(accessItem.getSampleValue()));

        String serializedJSON = objectMapper.writeValueAsString(group);
        assertEquals(objectMapper.readTree(serverJSON), objectMapper.readTree(serializedJSON));
    }
}