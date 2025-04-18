package com.netflix.conductor.common.metadata;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class TestSerDerAuditable {

    @Test
    public void testSerializationDeserialization() throws Exception {
        String serverJSON = "{\"updatedBy\":\"sample_updatedBy\",\"createTime\":123,\"createdBy\":\"sample_createdBy\",\"updateTime\":123,\"ownerApp\":\"sample_ownerApp\"}";
        ObjectMapper objectMapper = new ObjectMapper();

        Auditable auditable = objectMapper.readValue(serverJSON, Auditable.class);
        assertEquals("sample_updatedBy", auditable.getUpdatedBy());
        assertEquals(123L, auditable.getCreateTime());
        assertEquals("sample_createdBy", auditable.getCreatedBy());
        assertEquals(123L, auditable.getUpdateTime());
        assertEquals("sample_ownerApp", auditable.getOwnerApp());

        String serializedJSON = objectMapper.writeValueAsString(auditable);
        Auditable deserializedAuditable = objectMapper.readValue(serializedJSON, Auditable.class);
        assertEquals(auditable.getUpdatedBy(), deserializedAuditable.getUpdatedBy());
        assertEquals(auditable.getCreateTime(), deserializedAuditable.getCreateTime());
        assertEquals(auditable.getCreatedBy(), deserializedAuditable.getCreatedBy());
        assertEquals(auditable.getUpdateTime(), deserializedAuditable.getUpdateTime());
        assertEquals(auditable.getOwnerApp(), deserializedAuditable.getOwnerApp());

        assertEquals(objectMapper.readTree(serverJSON), objectMapper.readTree(serializedJSON));
    }
}