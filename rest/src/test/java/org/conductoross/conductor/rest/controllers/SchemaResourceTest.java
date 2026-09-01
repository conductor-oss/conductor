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

import org.conductoross.conductor.RequestBodyCoercionConfiguration;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
 * <p>The context is a real one rather than {@code MockMvcBuilders.standaloneSetup} because the
 * request body is the subject. Standalone setup builds its own plain {@link
 * com.fasterxml.jackson.databind.ObjectMapper}, so a body-shape assertion made against it says
 * nothing about the mapper the server actually parses with — including whether {@code
 * ACCEPT_SINGLE_VALUE_AS_ARRAY} is enabled, which is the whole reason half the client estate works.
 * Importing the two mapper configurations is what puts the production mapper under test.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = SchemaResourceTest.TestConfig.class)
@AutoConfigureMockMvc
public class SchemaResourceTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private SchemaService schemaService;

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
     * The Python, Ruby and Rust clients post a bare object, not a list. They work against the
     * commercial server only because it enables {@code ACCEPT_SINGLE_VALUE_AS_ARRAY}; without the
     * same setting here, half the shipped client estate breaks against OSS while the contract looks
     * identical.
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
        when(schemaService.saveSchemas(anyList(), eq(false)))
                .thenReturn(List.of(schema("order", 1)));

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
        when(schemaService.saveSchemas(anyList(), eq(false)))
                .thenThrow(new IllegalArgumentException("Schema name cannot be blank"));

        mockMvc.perform(
                        post("/api/schema")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("[{\"type\":\"JSON\"}]"))
                .andExpect(status().isBadRequest());
    }

    /**
     * A schema with no type is accepted at registration. The definitions that reference one are not
     * cascade-validated either, so refusing it here would reject a payload the rest of the server
     * takes; whether a null type is usable is a question for the validation gate, not this
     * resource.
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
        when(schemaService.getSchema("order")).thenReturn(schema("order", 3));

        mockMvc.perform(get("/api/schema/order"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("order"))
                .andExpect(jsonPath("$.version").value(3))
                .andExpect(jsonPath("$.type").value("JSON"));
    }

    @Test
    public void getsOneVersion() throws Exception {
        when(schemaService.getSchema("order", 2)).thenReturn(schema("order", 2));

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

    @Test
    public void shortListingCarriesOnlyNamesAndVersions() throws Exception {
        when(schemaService.getAllSchemas())
                .thenReturn(List.of(schema("order", 1), schema("payment", 4)));

        mockMvc.perform(get("/api/schema?short=true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("order"))
                .andExpect(jsonPath("$[0].version").value(1))
                .andExpect(jsonPath("$[0].data").doesNotExist())
                .andExpect(jsonPath("$[0].type").doesNotExist())
                .andExpect(jsonPath("$[1].name").value("payment"))
                .andExpect(jsonPath("$[1].version").value(4));
    }

    /**
     * No authenticated principal, so these are never set and the null-omitting mapper drops them.
     */
    @Test
    public void responsesCarryNoAuditFields() throws Exception {
        when(schemaService.getSchema("order")).thenReturn(schema("order", 1));

        mockMvc.perform(get("/api/schema/order"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdBy").doesNotExist())
                .andExpect(jsonPath("$.updatedBy").doesNotExist())
                .andExpect(jsonPath("$.ownerApp").doesNotExist())
                .andExpect(jsonPath("$.createTime").value(1000L))
                .andExpect(jsonPath("$.updateTime").value(2000L));
    }

    @Test
    public void missingSchemaIsNotFoundRatherThanEmpty() throws Exception {
        when(schemaService.getSchema("absent")).thenThrow(new NotFoundException("no such schema"));
        when(schemaService.getSchema("absent", 7))
                .thenThrow(new NotFoundException("no such schema"));

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

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    public void deletesEveryVersionByName() throws Exception {
        mockMvc.perform(delete("/api/schema/order")).andExpect(status().isOk());

        verify(schemaService).deleteSchema("order");
    }

    @Test
    public void deletesOneVersion() throws Exception {
        mockMvc.perform(delete("/api/schema/order/2")).andExpect(status().isOk());

        verify(schemaService).deleteSchema("order", 2);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private List<SchemaDef> captureSave(boolean newVersion) {
        ArgumentCaptor<List<SchemaDef>> captor = ArgumentCaptor.forClass(List.class);
        verify(schemaService).saveSchemas(captor.capture(), eq(newVersion));
        return captor.getValue();
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
        ObjectMapperConfiguration.class,
        RequestBodyCoercionConfiguration.class
    })
    static class TestConfig {

        @Bean
        public SchemaService schemaService() {
            return mock(SchemaService.class);
        }
    }
}
