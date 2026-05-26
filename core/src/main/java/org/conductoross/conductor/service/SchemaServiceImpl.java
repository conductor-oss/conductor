/*
 * Copyright 2026 Conductor Authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.conductoross.conductor.service;

import java.util.List;

import org.conductoross.conductor.dao.SchemaDAO;
import org.springframework.stereotype.Service;

import com.netflix.conductor.common.metadata.SchemaDef;
import com.netflix.conductor.core.WorkflowContext;
import com.netflix.conductor.core.exception.NotFoundException;

@Service
public class SchemaServiceImpl implements SchemaService {

    public static final String DEFAULT_ORG_ID = "default";

    private final SchemaDAO schemaDAO;

    public SchemaServiceImpl(SchemaDAO schemaDAO) {
        this.schemaDAO = schemaDAO;
    }

    @Override
    public void save(SchemaDef schemaDef, boolean newVersion) {
        long now = System.currentTimeMillis();
        if (newVersion) {
            int latestVersion =
                    schemaDAO.getLatestVersion(DEFAULT_ORG_ID, schemaDef.getName()).orElse(0);
            schemaDef.setVersion(latestVersion + 1);
            schemaDef.setCreateTime(now);
            schemaDef.setCreatedBy(WorkflowContext.get().getClientApp());
            schemaDef.setUpdateTime(null);
            schemaDef.setUpdatedBy(null);
        } else {
            schemaDAO.getSchemaByNameAndVersion(
                            DEFAULT_ORG_ID, schemaDef.getName(), schemaDef.getVersion())
                    .ifPresentOrElse(
                            existing -> {
                                schemaDef.setCreateTime(existing.getCreateTime());
                                schemaDef.setCreatedBy(existing.getCreatedBy());
                                schemaDef.setUpdateTime(now);
                                schemaDef.setUpdatedBy(WorkflowContext.get().getClientApp());
                            },
                            () -> {
                                schemaDef.setCreateTime(now);
                                schemaDef.setCreatedBy(WorkflowContext.get().getClientApp());
                                schemaDef.setUpdateTime(null);
                                schemaDef.setUpdatedBy(null);
                            });
        }
        schemaDAO.save(DEFAULT_ORG_ID, schemaDef);
    }

    @Override
    public SchemaDef getSchemaByNameWithLatestVersion(String name) {
        return schemaDAO.getSchemaByNameWithLatestVersion(DEFAULT_ORG_ID, name)
                .orElseThrow(() -> new NotFoundException("No such schema found by name: %s", name));
    }

    @Override
    public SchemaDef getSchemaByNameAndVersion(String name, Integer version) {
        return schemaDAO.getSchemaByNameAndVersion(DEFAULT_ORG_ID, name, version)
                .orElseThrow(
                        () ->
                                new NotFoundException(
                                        "No such schema found by name: %s, version: %d",
                                        name, version));
    }

    @Override
    public List<SchemaDef> getAllSchemas(boolean shortResult) {
        List<SchemaDef> schemas = schemaDAO.getAllSchemas(DEFAULT_ORG_ID);
        if (!shortResult) {
            return schemas;
        }
        return schemas.stream().map(this::toShortSchema).toList();
    }

    @Override
    public void deleteSchemaByName(String name) {
        if (schemaDAO.getSchemaByNameWithLatestVersion(DEFAULT_ORG_ID, name).isEmpty()) {
            throw new NotFoundException("No such schema found by name: %s", name);
        }
        schemaDAO.deleteSchemaByName(DEFAULT_ORG_ID, name);
    }

    @Override
    public void deleteSchemaByNameAndVersion(String name, Integer version) {
        if (schemaDAO.getSchemaByNameAndVersion(DEFAULT_ORG_ID, name, version).isEmpty()) {
            throw new NotFoundException(
                    "No such schema found by name: %s, version: %d", name, version);
        }
        schemaDAO.deleteSchemaByNameAndVersion(DEFAULT_ORG_ID, name, version);
    }

    private SchemaDef toShortSchema(SchemaDef schemaDef) {
        SchemaDef shortSchema = new SchemaDef();
        shortSchema.setName(schemaDef.getName());
        shortSchema.setVersion(schemaDef.getVersion());
        shortSchema.setType(schemaDef.getType());
        shortSchema.setCreatedBy(schemaDef.getCreatedBy());
        shortSchema.setCreateTime(schemaDef.getCreateTime());
        shortSchema.setUpdatedBy(schemaDef.getUpdatedBy());
        shortSchema.setUpdateTime(schemaDef.getUpdateTime());
        return shortSchema;
    }
}
