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
package io.conductor.e2e.schema;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.client.exception.ConductorClientException;
import com.netflix.conductor.client.http.ConductorClient;
import com.netflix.conductor.client.http.ConductorClientRequest;
import com.netflix.conductor.client.http.ConductorClientRequest.Method;
import com.netflix.conductor.common.metadata.SchemaDef;

import io.conductor.e2e.util.ApiUtil;
import io.orkes.conductor.client.http.OrkesSchemaClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The schema registry over HTTP, driven through the shipped Java SDK client.
 *
 * <p>The client is the point. It is what a user actually runs, and it sends a list where the
 * server's own unit tests could be made to send anything — which is how a request-body defect
 * reaches a running server while every test below the controller passes. The one test that does not
 * use the client sends a bare object instead of a list, because the Python, Ruby and Rust clients
 * do that and no Java client can reproduce it.
 *
 * <p>Every schema is named with a fresh UUID: the suite runs with {@code maxParallelForks = 4}
 * against one server, and a fixed name would have two test classes deleting each other's registry
 * entries.
 *
 * <p>Run via any of the {@code e2e/run_tests-*.sh} flavors.
 */
class SchemaRegistryE2ETest {

    private final OrkesSchemaClient schemaClient = ApiUtil.SCHEMA_CLIENT;
    private final ConductorClient client = ApiUtil.CLIENT;

    private final String name = "e2e-schema-" + UUID.randomUUID();

    @AfterEach
    void removeSchema() {
        try {
            schemaClient.deleteSchema(name);
        } catch (Exception ignored) {
            // Nothing was registered, or the test under study already removed it.
        }
    }

    @Test
    void savesAndReadsBackTheLatestVersion() {
        schemaClient.saveSchema(schema(1));

        SchemaDef found = schemaClient.getSchema(name);

        assertEquals(name, found.getName());
        assertEquals(1, found.getVersion());
        assertEquals(SchemaDef.Type.JSON, found.getType());
        assertEquals("object", found.getData().get("type"));
    }

    @Test
    void savesManySchemasInOneRequest() {
        String other = name + "-b";
        try {
            schemaClient.saveSchemas(List.of(schema(1), schema(other, 1)));

            assertEquals(name, schemaClient.getSchema(name).getName());
            assertEquals(other, schemaClient.getSchema(other).getName());
        } finally {
            schemaClient.deleteSchema(other);
        }
    }

    @Test
    void readsOneVersionByNameAndVersion() {
        schemaClient.saveSchemas(List.of(schema(1), schema(2)));

        assertEquals(1, schemaClient.getSchema(name, 1).getVersion());
        assertEquals(2, schemaClient.getSchema(name, 2).getVersion());
        assertEquals(2, schemaClient.getSchema(name).getVersion(), "latest is the highest version");
    }

    @Test
    void listsEveryVersionOfEverySchema() {
        schemaClient.saveSchemas(List.of(schema(1), schema(2)));

        List<SchemaDef> mine =
                schemaClient.getAllSchemas(false).stream()
                        .filter(schema -> name.equals(schema.getName()))
                        .toList();

        assertEquals(2, mine.size());
        assertTrue(mine.stream().allMatch(schema -> schema.getData() != null));
    }

    @Test
    void shortListingCarriesNamesAndVersionsOnly() {
        schemaClient.saveSchema(schema(1));

        List<SchemaDef> mine =
                schemaClient.getAllSchemas(true).stream()
                        .filter(schema -> name.equals(schema.getName()))
                        .toList();

        assertEquals(1, mine.size());
        assertEquals(1, mine.get(0).getVersion());
        assertNull(mine.get(0).getData(), "the short listing exists to omit the body");
        assertNull(mine.get(0).getType());
    }

    @Test
    void deletesOneVersionAndLeavesTheRest() {
        schemaClient.saveSchemas(List.of(schema(1), schema(2)));

        schemaClient.deleteSchema(name, 2);

        assertEquals(1, schemaClient.getSchema(name).getVersion());
        assertNotFound(() -> schemaClient.getSchema(name, 2));
    }

    @Test
    void deletesEveryVersionByName() {
        schemaClient.saveSchemas(List.of(schema(1), schema(2)));

        schemaClient.deleteSchema(name);

        assertNotFound(() -> schemaClient.getSchema(name));
        assertNotFound(() -> schemaClient.getSchema(name, 1));
    }

    /**
     * {@code newVersion=true} is the only parameter in the contract no shipped Java client sends,
     * so the request is built by hand. A picker that saves an edited schema relies on it.
     */
    @Test
    void newVersionAllocatesOnePastTheHighestVersion() {
        schemaClient.saveSchema(schema(1));

        saveWithNewVersion(schema(1));

        assertEquals(2, schemaClient.getSchema(name).getVersion());
        assertEquals(1, schemaClient.getSchema(name, 1).getVersion(), "version 1 is left alone");
    }

    @Test
    void unknownSchemaIs404() {
        assertNotFound(() -> schemaClient.getSchema("never-registered-" + UUID.randomUUID()));
    }

    /**
     * The Python, Ruby and Rust schema clients post a bare object rather than a list. This is the
     * request they send, and it is the one the previous attempt at this feature answered with a
     * 500.
     */
    @Test
    void acceptsABareObjectWhereTheContractDeclaresAList() {
        client.execute(
                ConductorClientRequest.builder()
                        .method(Method.POST)
                        .path("/schema")
                        .body(
                                Map.of(
                                        "name",
                                        name,
                                        "version",
                                        1,
                                        "type",
                                        "JSON",
                                        "data",
                                        Map.of("type", "object")))
                        .build());

        SchemaDef found = schemaClient.getSchema(name);
        assertEquals(name, found.getName());
        assertEquals(1, found.getVersion());
    }

    /** No authenticated principal on an OSS server, so nothing populates these. */
    @Test
    void responsesCarryNoCreatedByOrUpdatedBy() {
        schemaClient.saveSchema(schema(1));

        SchemaDef found = schemaClient.getSchema(name);

        assertNull(found.getCreatedBy());
        assertNull(found.getUpdatedBy());
        assertNotNull(found.getCreateTime());
        assertTrue(found.getCreateTime() > 0, "timestamps are kept");
    }

    private void saveWithNewVersion(SchemaDef schema) {
        client.execute(
                ConductorClientRequest.builder()
                        .method(Method.POST)
                        .path("/schema")
                        .addQueryParam("newVersion", true)
                        .body(List.of(schema))
                        .build());
    }

    private SchemaDef schema(int version) {
        return schema(name, version);
    }

    private static SchemaDef schema(String name, int version) {
        return SchemaDef.builder()
                .name(name)
                .version(version)
                .type(SchemaDef.Type.JSON)
                .data(Map.of("type", "object", "title", "v" + version))
                .build();
    }

    private static void assertNotFound(Runnable call) {
        ConductorClientException e = assertThrows(ConductorClientException.class, call::run);
        assertEquals(404, e.getStatusCode(), "Expected 404 but got: " + e);
    }
}
