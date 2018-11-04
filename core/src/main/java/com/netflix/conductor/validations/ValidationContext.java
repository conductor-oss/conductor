package com.netflix.conductor.validations;

import com.netflix.conductor.dao.MetadataDAO;

public class ValidationContext {

    private static MetadataDAO metadataDAO;

    public static void initialize(MetadataDAO metadataDAO) {
        ValidationContext.metadataDAO = metadataDAO;
    }

    public static MetadataDAO getMetadataDAO() {
        return metadataDAO;
    }

}
