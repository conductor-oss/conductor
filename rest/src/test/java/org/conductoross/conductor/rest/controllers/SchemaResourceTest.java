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
package org.conductoross.conductor.rest.controllers;

import java.util.List;
import java.util.Map;

import org.conductoross.conductor.controllers.SchemaResource;
import org.conductoross.conductor.service.SchemaService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import com.netflix.conductor.common.config.ObjectMapperBuilderConfiguration;
import com.netflix.conductor.common.config.ObjectMapperConfiguration;
import com.netflix.conductor.common.metadata.SchemaDef;
import com.netflix.conductor.core.exception.NotFoundException;
import com.netflix.conductor.rest.controllers.ApplicationExceptionMapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The HTTP contract for {@code /api/schema}, as the shipped SDK clients see it.
 *
 * <p>Deliberately a fast duplicate of what the end-to-end suite covers: that suite needs a Docker
 * daemon and several minutes, this runs in seconds in the always-run job, and the request-body
 * shape is the single most likely thing in this feature to be got wrong — a bare object where the
 * server wants a list reaches a live 500 while every unit test below the controller passes.
 *
 * <p>The coercion this relies on is configured on the shipped server by {@code
 * spring.jackson.deserialization.accept-single-value-as-array} in the server module's {@code
 * application.properties}, which is not on this module's classpath, so the property is restated
 * here. That means this class proves the contract holds <em>given</em> the setting, not that the
 * setting ships: deleting it from {@code application.properties} would leave these tests green.
 * {@code ShippedJacksonPropertiesTest} in the server module is what fails in that case, and {@code
 * SchemaRegistryE2ETest.acceptsABareObjectWhereTheContractDeclaresAList} covers the whole path
 * against a real server image.
 *
 * <p>The context is a real one rather than {@code MockMvcBuilders.standaloneSetup} because the
 * request body is the subject. Standalone setup builds its own plain {@link
 * com.fasterxml.jackson.databind.ObjectMapper}, so a body-shape assertion made against it says
 * nothing about the mapper the server actually parses with — including whether {@code
 * ACCEPT_SINGLE_VALUE_AS_ARRAY} is enabled, which is the whole reason half the client estate works.
 * Importing the two mapper configurations is what puts the production mapper under test.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(
        classes = SchemaResourceTest.TestConfig.class,
        properties = "spring.jackson.deserialization.accept-single-value-as-array=true")
@AutoConfigureMockMvc
public class SchemaResourceTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private SchemaService schemaService;
    @Autowired private SchemaResource schemaResource;

    @Before
    public void setUp() {
        reset(schemaService);
    }

    // ── save ──────────────────────────────────────────────────────────────────

    @Test
    public void savesAListOfSchemas() throws Exception {
        mockMvc.perform(
                        post("/api/schema")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "[{\"name\":\"order\",\"version\":1,\"type\":\"JSON\","
                                                + "\"data\":{\"type\":\"object\"}}]"))
                .andExpect(status().isOk());

        List<SchemaDef> saved = captureSave(false);
        assertEquals(1, saved.size());
        assertEquals("order", saved.get(0).getName());
        assertEquals(1, saved.get(0).getVersion());
        assertEquals(SchemaDef.Type.JSON, saved.get(0).getType());
        assertEquals(Map.of("type", "object"), saved.get(0).getData());
    }

    /**
     * The Python, Ruby and Rust clients post a bare object, not a list. They only work where {@code
     * ACCEPT_SINGLE_VALUE_AS_ARRAY} is enabled; without that setting, half the shipped client
     * estate breaks against this server while the contract looks identical.
     */
    @Test
    public void savesABareObjectAsASingleElementList() throws Exception {
        mockMvc.perform(
                        post("/api/schema")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"order\",\"version\":2,\"type\":\"JSON\"}"))
                .andExpect(status().isOk());

        List<SchemaDef> saved = captureSave(false);
        assertEquals(1, saved.size());
        assertEquals("order", saved.get(0).getName());
        assertEquals(2, saved.get(0).getVersion());
    }

    @Test
    public void newVersionDefaultsToFalseAndIsPassedThroughWhenSet() throws Exception {
        mockMvc.perform(
                        post("/api/schema")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("[{\"name\":\"order\",\"type\":\"JSON\"}]"))
                .andExpect(status().isOk());
        captureSave(false);

        reset(schemaService);

        mockMvc.perform(
                        post("/api/schema?newVersion=true")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("[{\"name\":\"order\",\"type\":\"JSON\"}]"))
                .andExpect(status().isOk());
        captureSave(true);
    }

    /**
     * The contract returns no body, so an SDK generated from this server declares the call void.
     */
    @Test
    public void saveReturnsNoBody() throws Exception {
        when(schemaService.saveSchema(any(SchemaDef.class), eq(false)))
                .thenReturn(schema("order", 1));

        String body =
                mockMvc.perform(
                                post("/api/schema")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("[{\"name\":\"order\",\"type\":\"JSON\"}]"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        assertEquals("", body);
    }

    @Test
    public void blankNameIsRejected() throws Exception {
        when(schemaService.saveSchema(any(SchemaDef.class), eq(false)))
                .thenThrow(new IllegalArgumentException("Schema name cannot be blank"));

        mockMvc.perform(
                        post("/api/schema")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("[{\"type\":\"JSON\"}]"))
                .andExpect(status().isBadRequest());
    }

    /**
     * A schema with no type is stored, despite {@link SchemaDef} declaring {@code @NotNull} on
     * {@code type} and the body carrying {@code @Valid}.
     *
     * <p>{@code @Valid} on a {@code List} parameter validates the list itself, which has no
     * constraints; cascading into the elements would need {@code List<@Valid SchemaDef>}. So the
     * annotation is inert here, and this test is what says so — without it, someone reading the
     * signature would reasonably assume this request is rejected.
     *
     * <p>The behaviour is right either way: task and workflow definitions carry schemas with no
     * cascading validation of their own, so a type-less schema is registrable through those paths,
     * and refusing it only here would make the two disagree. {@code SchemaService.validate} reports
     * it when such a schema is actually used.
     */
    @Test
    public void schemaWithNoTypeIsStored() throws Exception {
        mockMvc.perform(
                        post("/api/schema")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("[{\"name\":\"order\",\"version\":1}]"))
                .andExpect(status().isOk());

        assertNull(captureSave(false).get(0).getType());
    }

    // ── read ──────────────────────────────────────────────────────────────────

    @Test
    public void getsLatestVersionByName() throws Exception {
        when(schemaService.getSchemaByNameWithLatestVersion("order"))
                .thenReturn(schema("order", 3));

        mockMvc.perform(get("/api/schema/order"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("order"))
                .andExpect(jsonPath("$.version").value(3))
                .andExpect(jsonPath("$.type").value("JSON"));
    }

    @Test
    public void getsOneVersion() throws Exception {
        when(schemaService.getSchemaByNameAndVersion("order", 2)).thenReturn(schema("order", 2));

        mockMvc.perform(get("/api/schema/order/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2));
    }

    @Test
    public void listsEverySchema() throws Exception {
        when(schemaService.getAllSchemas())
                .thenReturn(List.of(schema("order", 1), schema("payment", 1)));

        mockMvc.perform(get("/api/schema"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("order"))
                .andExpect(jsonPath("$[0].data").exists())
                .andExpect(jsonPath("$[1].name").value("payment"));
    }

    /**
     * {@code short=true} reads the backend's name-and-version projection rather than listing every
     * schema and blanking it here, so the bodies are never fetched at all.
     */
    @Test
    public void shortListingCarriesOnlyNamesAndVersions() throws Exception {
        when(schemaService.getAllShortenedSchemas())
                .thenReturn(List.of(nameAndVersion("order", 1), nameAndVersion("payment", 4)));

        mockMvc.perform(get("/api/schema?short=true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("order"))
                .andExpect(jsonPath("$[0].version").value(1))
                .andExpect(jsonPath("$[0].data").doesNotExist())
                .andExpect(jsonPath("$[0].type").doesNotExist())
                .andExpect(jsonPath("$[1].name").value("payment"))
                .andExpect(jsonPath("$[1].version").value(4));

        verify(schemaService, never()).getAllSchemas();
    }

    /**
     * No authenticated principal, so these are never set and the null-omitting mapper drops them.
     */
    @Test
    public void responsesCarryNoAuditFields() throws Exception {
        when(schemaService.getSchemaByNameWithLatestVersion("order"))
                .thenReturn(schema("order", 1));

        mockMvc.perform(get("/api/schema/order"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdBy").doesNotExist())
                .andExpect(jsonPath("$.updatedBy").doesNotExist())
                .andExpect(jsonPath("$.ownerApp").doesNotExist())
                .andExpect(jsonPath("$.createTime").value(1000L))
                .andExpect(jsonPath("$.updateTime").value(2000L));
    }

    /**
     * The service reports an unregistered schema as {@code null}; turning that into a 404 is this
     * resource's job, and this is where that is pinned.
     */
    @Test
    public void missingSchemaIsNotFoundRatherThanEmpty() throws Exception {
        when(schemaService.getSchemaByNameWithLatestVersion("absent")).thenReturn(null);
        when(schemaService.getSchemaByNameAndVersion("absent", 7)).thenReturn(null);

        mockMvc.perform(get("/api/schema/absent")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/schema/absent/7")).andExpect(status().isNotFound());
    }

    @Test
    public void emptyRegistryListsAsAnEmptyArray() throws Exception {
        when(schemaService.getAllSchemas()).thenReturn(List.of());

        mockMvc.perform(get("/api/schema"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    /**
     * A null version means none was asked for, and reads the latest. The routes always supply the
     * path variable, so this is reachable only by calling the method — which is what a caller
     * inside the server does, and why the branch exists.
     */
    @Test
    public void aNullVersionReadsTheLatest() {
        when(schemaService.getSchemaByNameWithLatestVersion("order"))
                .thenReturn(schema("order", 9));

        assertEquals(9, schemaResource.getSchemaByNameAndVersion("order", null).getVersion());
        verify(schemaService, never()).getSchemaByNameAndVersion(eq("order"), anyInt());
    }

    /** The same rule on delete: the latest version goes, not the whole history. */
    @Test
    public void aNullVersionDeletesTheLatest() {
        when(schemaService.getSchemaByNameWithLatestVersion("order"))
                .thenReturn(schema("order", 9));

        schemaResource.deleteSchemaByNameAndVersion("order", null);

        verify(schemaService).deleteSchemaByNameAndVersion("order", 9);
        verify(schemaService, never()).deleteSchemaByName("order");
    }

    /** A null version on a name with nothing registered is still a 404, not a silent no-op. */
    @Test
    public void aNullVersionOnAnUnknownNameIsNotFound() {
        when(schemaService.getSchemaByNameWithLatestVersion("absent")).thenReturn(null);

        assertThrows(
                NotFoundException.class,
                () -> schemaResource.deleteSchemaByNameAndVersion("absent", null));
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    public void deletesEveryVersionByName() throws Exception {
        mockMvc.perform(delete("/api/schema/order")).andExpect(status().isOk());

        verify(schemaService).deleteSchemaByName("order");
    }

    @Test
    public void deletesOneVersion() throws Exception {
        mockMvc.perform(delete("/api/schema/order/2")).andExpect(status().isOk());

        verify(schemaService).deleteSchemaByNameAndVersion("order", 2);
    }

    /**
     * Deleting something that is not registered is a 404, not a quiet 200. The service reports it
     * by throwing, unlike the read path, so this only checks the status survives the trip out.
     */
    @Test
    public void deletingSomethingUnregisteredIsNotFound() throws Exception {
        doThrow(new NotFoundException("No schema found by name absent"))
                .when(schemaService)
                .deleteSchemaByName("absent");
        doThrow(new NotFoundException("No schema found by name absent and version 7"))
                .when(schemaService)
                .deleteSchemaByNameAndVersion("absent", 7);

        mockMvc.perform(delete("/api/schema/absent")).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/schema/absent/7")).andExpect(status().isNotFound());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /** A registry entry as the shortened listing returns it: identity, no document. */
    private static SchemaDef nameAndVersion(String name, int version) {
        SchemaDef schema = new SchemaDef();
        schema.setName(name);
        schema.setVersion(version);
        return schema;
    }

    private List<SchemaDef> captureSave(boolean newVersion) {
        ArgumentCaptor<SchemaDef> captor = ArgumentCaptor.forClass(SchemaDef.class);
        verify(schemaService, atLeastOnce()).saveSchema(captor.capture(), eq(newVersion));
        return captor.getAllValues();
    }

    private static SchemaDef schema(String name, int version) {
        SchemaDef schema =
                SchemaDef.builder()
                        .name(name)
                        .version(version)
                        .type(SchemaDef.Type.JSON)
                        .data(Map.of("type", "object"))
                        .build();
        schema.setCreateTime(1000L);
        schema.setUpdateTime(2000L);
        return schema;
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({
        SchemaResource.class,
        ApplicationExceptionMapper.class,
        ObjectMapperBuilderConfiguration.class,
        ObjectMapperConfiguration.class
    })
    static class TestConfig {

        @Bean
        public SchemaService schemaService() {
            return mock(SchemaService.class);
        }
    }
}
