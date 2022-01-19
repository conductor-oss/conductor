/*
 * Copyright 2022 Netflix, Inc.
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
package com.netflix.conductor.postgres.storage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.netflix.conductor.common.config.TestObjectMapperConfiguration;

import static org.junit.Assert.assertEquals;

@ContextConfiguration(classes = {TestObjectMapperConfiguration.class})
@RunWith(SpringRunner.class)
public class PostgresPayloadStorageTest {

    private PostgresPayloadTestUtil testPostgres;
    private PostgresPayloadStorage executionPostgres;

    public PostgreSQLContainer<?> postgreSQLContainer;

    private final String inputString =
            "Lorem Ipsum is simply dummy text of the printing and typesetting industry."
                    + " Lorem Ipsum has been the industry's standard dummy text ever since the 1500s.";
    private final InputStream inputData;
    private final String key = "dummyKey.json";

    public PostgresPayloadStorageTest() {
        inputData = new ByteArrayInputStream(inputString.getBytes(StandardCharsets.UTF_8));
    }

    @Before
    public void setup() {
        postgreSQLContainer =
                new PostgreSQLContainer<>(DockerImageName.parse("postgres"))
                        .withDatabaseName("conductor");
        postgreSQLContainer.start();
        testPostgres = new PostgresPayloadTestUtil(postgreSQLContainer);
        executionPostgres =
                new PostgresPayloadStorage(
                        testPostgres.getTestProperties(), testPostgres.getDataSource());
    }

    @Test
    public void testWriteInputStreamToDb() throws IOException, SQLException {
        executionPostgres.upload(key, inputData, inputData.available());

        PreparedStatement stmt =
                testPostgres
                        .getDataSource()
                        .getConnection()
                        .prepareStatement(
                                "SELECT data FROM external.external_payload WHERE id = 'dummyKey.json'");
        ResultSet rs = stmt.executeQuery();
        rs.next();
        assertEquals(
                inputString,
                new String(rs.getBinaryStream(1).readAllBytes(), StandardCharsets.UTF_8));
    }

    @Test
    public void testReadInputStreamFromDb() throws IOException, SQLException {
        PreparedStatement stmt =
                testPostgres
                        .getDataSource()
                        .getConnection()
                        .prepareStatement("INSERT INTO external.external_payload  VALUES (?, ?)");
        stmt.setString(1, key);
        stmt.setBinaryStream(2, inputData, inputData.available());
        stmt.executeUpdate();

        assertEquals(
                inputString,
                new String(executionPostgres.download(key).readAllBytes(), StandardCharsets.UTF_8));
    }

    @Test
    public void testMaxRowInTable() throws IOException, SQLException {
        executionPostgres.upload(key, inputData, inputData.available());
        executionPostgres.upload("dummyKey2.json", inputData, inputData.available());
        executionPostgres.upload("dummyKey3.json", inputData, inputData.available());
        executionPostgres.upload("dummyKey4.json", inputData, inputData.available());
        executionPostgres.upload("dummyKey5.json", inputData, inputData.available());
        executionPostgres.upload("dummyKey6.json", inputData, inputData.available());
        executionPostgres.upload("dummyKey7.json", inputData, inputData.available());

        PreparedStatement stmt =
                testPostgres
                        .getDataSource()
                        .getConnection()
                        .prepareStatement("SELECT count(id) FROM external.external_payload");
        ResultSet rs = stmt.executeQuery();
        rs.next();
        assertEquals(5, rs.getInt(1));
        stmt.close();
    }

    @After
    public void teardown() throws SQLException {
        testPostgres.getDataSource().getConnection().close();
    }
}
