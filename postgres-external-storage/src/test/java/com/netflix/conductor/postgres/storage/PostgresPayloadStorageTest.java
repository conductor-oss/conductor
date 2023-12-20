/*
 * Copyright 2023 Conductor Authors.
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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.netflix.conductor.common.config.TestObjectMapperConfiguration;
import com.netflix.conductor.common.utils.ExternalPayloadStorage;
import com.netflix.conductor.core.utils.IDGenerator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

@ContextConfiguration(classes = {TestObjectMapperConfiguration.class})
@RunWith(SpringRunner.class)
public class PostgresPayloadStorageTest {

    private PostgresPayloadTestUtil testPostgres;
    private PostgresPayloadStorage executionPostgres;

    public PostgreSQLContainer<?> postgreSQLContainer;

    private final String inputString =
            "Lorem Ipsum is simply dummy text of the printing and typesetting industry."
                    + " Lorem Ipsum has been the industry's standard dummy text ever since the 1500s.";
    private final String errorMessage = "{\"Error\": \"Data does not exist.\"}";
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
                        testPostgres.getTestProperties(),
                        testPostgres.getDataSource(),
                        new IDGenerator(),
                        errorMessage);
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
        insertData();
        assertEquals(
                inputString,
                new String(executionPostgres.download(key).readAllBytes(), StandardCharsets.UTF_8));
    }

    private void insertData() throws SQLException, IOException {
        PreparedStatement stmt =
                testPostgres
                        .getDataSource()
                        .getConnection()
                        .prepareStatement("INSERT INTO external.external_payload  VALUES (?, ?)");
        stmt.setString(1, key);
        stmt.setBinaryStream(2, inputData, inputData.available());
        stmt.executeUpdate();
    }

    @Test(timeout = 60 * 1000)
    public void testMultithreadDownload()
            throws ExecutionException, InterruptedException, SQLException, IOException {
        AtomicInteger threadCounter = new AtomicInteger(0);
        insertData();
        int numberOfThread = 12;
        int taskInThread = 100;
        ArrayList<CompletableFuture<?>> completableFutures = new ArrayList<>();
        Executor executor = Executors.newFixedThreadPool(numberOfThread);
        IntStream.range(0, numberOfThread * taskInThread)
                .forEach(
                        i ->
                                createFutureForDownloadOperation(
                                        threadCounter, completableFutures, executor));
        for (CompletableFuture<?> completableFuture : completableFutures) {
            completableFuture.get();
        }
        assertCount(1);
        assertEquals(numberOfThread * taskInThread, threadCounter.get());
    }

    private void createFutureForDownloadOperation(
            AtomicInteger threadCounter,
            ArrayList<CompletableFuture<?>> completableFutures,
            Executor executor) {
        CompletableFuture<Void> objectCompletableFuture =
                CompletableFuture.supplyAsync(() -> downloadData(threadCounter), executor);
        completableFutures.add(objectCompletableFuture);
    }

    private Void downloadData(AtomicInteger threadCounter) {
        try {
            assertEquals(
                    inputString,
                    new String(
                            executionPostgres.download(key).readAllBytes(),
                            StandardCharsets.UTF_8));
            threadCounter.getAndIncrement();
            return null;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testReadNonExistentInputStreamFromDb() throws IOException, SQLException {
        PreparedStatement stmt =
                testPostgres
                        .getDataSource()
                        .getConnection()
                        .prepareStatement("INSERT INTO external.external_payload  VALUES (?, ?)");
        stmt.setString(1, key);
        stmt.setBinaryStream(2, inputData, inputData.available());
        stmt.executeUpdate();

        assertEquals(
                errorMessage,
                new String(
                        executionPostgres.download("non_existent_key.json").readAllBytes(),
                        StandardCharsets.UTF_8));
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

        assertCount(5);
    }

    @Test(timeout = 60 * 1000)
    public void testMultithreadInsert()
            throws SQLException, ExecutionException, InterruptedException {
        AtomicInteger threadCounter = new AtomicInteger(0);
        int numberOfThread = 12;
        int taskInThread = 100;
        ArrayList<CompletableFuture<?>> completableFutures = new ArrayList<>();
        Executor executor = Executors.newFixedThreadPool(numberOfThread);
        IntStream.range(0, numberOfThread * taskInThread)
                .forEach(
                        i ->
                                createFutureForUploadOperation(
                                        threadCounter, completableFutures, executor));
        for (CompletableFuture<?> completableFuture : completableFutures) {
            completableFuture.get();
        }
        assertCount(1);
        assertEquals(numberOfThread * taskInThread, threadCounter.get());
    }

    private void createFutureForUploadOperation(
            AtomicInteger threadCounter,
            ArrayList<CompletableFuture<?>> completableFutures,
            Executor executor) {
        CompletableFuture<Void> objectCompletableFuture =
                CompletableFuture.supplyAsync(() -> uploadData(threadCounter), executor);
        completableFutures.add(objectCompletableFuture);
    }

    private Void uploadData(AtomicInteger threadCounter) {
        try {
            uploadData();
            threadCounter.getAndIncrement();
            return null;
        } catch (IOException | SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testHashEnsuringNoDuplicates()
            throws IOException, SQLException, InterruptedException {
        final String createdOn = uploadData();
        Thread.sleep(500);
        final String createdOnAfterUpdate = uploadData();
        assertCount(1);
        assertNotEquals(createdOnAfterUpdate, createdOn);
    }

    private String uploadData() throws SQLException, IOException {
        final String location = getKey(inputString);
        ByteArrayInputStream inputStream =
                new ByteArrayInputStream(inputString.getBytes(StandardCharsets.UTF_8));
        executionPostgres.upload(location, inputStream, inputStream.available());
        return getCreatedOn(location);
    }

    @Test
    public void testDistinctHashedKey() {
        final String location = getKey(inputString);
        final String location2 = getKey(inputString);
        final String location3 = getKey(inputString + "A");

        assertNotEquals(location3, location);
        assertEquals(location2, location);
    }

    private String getKey(String input) {
        return executionPostgres
                .getLocation(
                        ExternalPayloadStorage.Operation.READ,
                        ExternalPayloadStorage.PayloadType.TASK_INPUT,
                        "",
                        input.getBytes(StandardCharsets.UTF_8))
                .getUri();
    }

    private void assertCount(int expected) throws SQLException {
        try (PreparedStatement stmt =
                        testPostgres
                                .getDataSource()
                                .getConnection()
                                .prepareStatement(
                                        "SELECT count(id) FROM external.external_payload");
                ResultSet rs = stmt.executeQuery()) {
            rs.next();
            assertEquals(expected, rs.getInt(1));
        }
    }

    private String getCreatedOn(String key) throws SQLException {
        try (Connection conn = testPostgres.getDataSource().getConnection();
                PreparedStatement stmt =
                        conn.prepareStatement(
                                "SELECT created_on FROM external.external_payload WHERE id = ?")) {
            stmt.setString(1, key);
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    @After
    public void teardown() throws SQLException {
        testPostgres.getDataSource().getConnection().close();
    }
}
