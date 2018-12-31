package com.netflix.conductor.validations;

import com.netflix.conductor.dao.MetadataDAO;

/**
 * This context is define to get access to {@link MetadataDAO} inside
 * {@link WorkflowTaskValidConstraint} constraint validator to validate
 * {@link com.netflix.conductor.common.metadata.workflow.WorkflowTask}.
 */
public class ValidationContext {

    private static MetadataDAO metadataDAO;

    public static void initialize(MetadataDAO metadataDAO) {
        ValidationContext.metadataDAO = metadataDAO;
    }

    public static MetadataDAO getMetadataDAO() {
        return metadataDAO;
    }

}
