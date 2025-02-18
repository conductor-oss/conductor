package com.netflix.conductor.common.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import lombok.SneakyThrows;

import java.security.MessageDigest;
import java.util.Base64;
import java.util.Objects;

public final class MetadataUtils {

    private static final ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();

    /**
     * Returns true is both workflow definitions have matching checksums.
     *
     * @param def0
     * @param def1
     * @return
     */
    public static boolean areChecksumsEqual(WorkflowDef def0, WorkflowDef def1) {
        String checksum1 = computeChecksum(def0);
        String checksum2 = computeChecksum(def1);
        return checksum2.equals(checksum1);
    }

    /**
     * Returns the computed checksum for the workflowDefinition.
     *
     * @param workflowDef
     * @return
     */
    @SneakyThrows
    public static String computeChecksum(WorkflowDef workflowDef) {
        Objects.requireNonNull(workflowDef, "WorkflowDef must not be null");
        //TODO consider replacing this client-side implementation with a call to the server in MetadataClient.
        WorkflowDef def = objectMapper.readValue(objectMapper.writeValueAsString(workflowDef), WorkflowDef.class);
        def.setCreateTime(0L);
        def.setUpdateTime(0L);
        def.setOwnerEmail(null);
        def.collectTasks().forEach(task -> {
            if (task.getTaskDefinition() != null) {
                task.getTaskDefinition().setCreateTime(0L);
                task.getTaskDefinition().setUpdateTime(0L);
                task.getTaskDefinition().setOwnerEmail("ignored@orkes.io");
            }
        });
        byte[] bytes = objectMapper.writeValueAsBytes(def);
        return encode(MessageDigest.getInstance("MD5").digest(bytes));
    }

    private static String encode(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }
}
