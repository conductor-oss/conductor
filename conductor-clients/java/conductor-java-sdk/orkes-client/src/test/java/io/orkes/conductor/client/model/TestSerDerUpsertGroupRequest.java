public class TestSerDerUpsertGroupRequest {

    @org.junit.jupiter.api.Test
    public void testSerializationDeserialization() throws Exception {
        String serverJson = "{\"roles\":[\"sample_roles\"],\"defaultAccess\":{\"${ResourceType}\":[{\"values\":[\"CREATE\",\"READ\",\"EXECUTE\",\"UPDATE\",\"DELETE\"],\"constants\":{\"READ\":\"(100)\",\"EXECUTE\":\"(200)\",\"DELETE\":\"(400)\",\"CREATE\":\"(0)\",\"UPDATE\":\"(300)\"},\"sampleValue\":\"CREATE\"}]},\"description\":\"sample_description\"}";

        com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        io.orkes.conductor.client.model.UpsertGroupRequest request = objectMapper.readValue(serverJson, io.orkes.conductor.client.model.UpsertGroupRequest.class);

        org.junit.jupiter.api.Assertions.assertNotNull(request);
        org.junit.jupiter.api.Assertions.assertEquals("sample_description", request.description);

        org.junit.jupiter.api.Assertions.assertNotNull(request.defaultAccess);
        org.junit.jupiter.api.Assertions.assertTrue(request.defaultAccess.containsKey("${ResourceType}"));
        java.util.List<java.util.Map<String, Object>> accessList = (java.util.List<java.util.Map<String, Object>>) request.defaultAccess.get("${ResourceType}");
        org.junit.jupiter.api.Assertions.assertEquals(1, accessList.size());
        java.util.Map<String, Object> accessItem = accessList.get(0);

        java.util.List<String> values = (java.util.List<String>) accessItem.get("values");
        org.junit.jupiter.api.Assertions.assertEquals(java.util.Arrays.asList("CREATE","READ","EXECUTE","UPDATE","DELETE"), values);

        java.util.Map<String, String> constants = (java.util.Map<String, String>) accessItem.get("constants");
        org.junit.jupiter.api.Assertions.assertEquals("(100)", constants.get("READ"));
        org.junit.jupiter.api.Assertions.assertEquals("(200)", constants.get("EXECUTE"));
        org.junit.jupiter.api.Assertions.assertEquals("(400)", constants.get("DELETE"));
        org.junit.jupiter.api.Assertions.assertEquals("(0)", constants.get("CREATE"));
        org.junit.jupiter.api.Assertions.assertEquals("(300)", constants.get("UPDATE"));

        org.junit.jupiter.api.Assertions.assertEquals("CREATE", accessItem.get("sampleValue"));

        String serializedJson = objectMapper.writeValueAsString(request);
        com.fasterxml.jackson.databind.JsonNode originalNode = objectMapper.readTree(serverJson);
        com.fasterxml.jackson.databind.JsonNode serializedNode = objectMapper.readTree(serializedJson);
        org.junit.jupiter.api.Assertions.assertEquals(originalNode, serializedNode);
    }
}