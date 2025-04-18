public class TestSerDerUpsertUserRequest {

    @Test
    public void testSerializationDeserialization() throws Exception {
        String serverJson = "{\"roles\":[\"ADMIN\"],\"name\":\"sample_name\",\"groups\":[\"sample_groups\"]}";
        ObjectMapper objectMapper = new ObjectMapper();
        UpsertUserRequest request = objectMapper.readValue(serverJson, UpsertUserRequest.class);
        
        Assertions.assertNotNull(request);
        Assertions.assertEquals("sample_name", request.getName());
        Assertions.assertNotNull(request.getGroups());
        Assertions.assertEquals(1, request.getGroups().size());
        Assertions.assertEquals("sample_groups", request.getGroups().get(0));
        Assertions.assertNotNull(request.getRoles());
        Assertions.assertEquals(1, request.getRoles().size());
        Assertions.assertEquals(UpsertUserRequest.RolesEnum.ADMIN, request.getRoles().get(0));
        
        String serializedJson = objectMapper.writeValueAsString(request);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode original = mapper.readTree(serverJson);
        JsonNode serialized = mapper.readTree(serializedJson);
        Assertions.assertEquals(original, serialized);
    }
}