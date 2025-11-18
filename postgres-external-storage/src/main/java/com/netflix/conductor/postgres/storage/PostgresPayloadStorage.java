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
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.function.Supplier;

import javax.sql.DataSource;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.common.run.ExternalStorageLocation;
import com.netflix.conductor.common.utils.ExternalPayloadStorage;
import com.netflix.conductor.core.exception.NonTransientException;
import com.netflix.conductor.core.utils.IDGenerator;
import com.netflix.conductor.postgres.config.PostgresPayloadProperties;

/**
 * Store and pull the external payload which consists of key and stream of data in PostgreSQL
 * database
 */
public class PostgresPayloadStorage implements ExternalPayloadStorage {

    private static final Logger LOGGER = LoggerFactory.getLogger(PostgresPayloadStorage.class);
    public static final String URI_SUFFIX_HASHED = ".hashed.json";
    public static final String URI_SUFFIX = ".json";
    public static final String URI_PREFIX_EXTERNAL = "/api/external/postgres/";
    private final String defaultMessageToUser;

    private final DataSource postgresDataSource;

    private final IDGenerator idGenerator;

    private final String tableName;
    private final String conductorUrl;

    public PostgresPayloadStorage(
            PostgresPayloadProperties properties,
            DataSource dataSource,
            IDGenerator idGenerator,
            String defaultMessageToUser) {
        tableName = properties.getTableName();
        conductorUrl = properties.getConductorUrl();
        this.postgresDataSource = dataSource;
        this.idGenerator = idGenerator;
        this.defaultMessageToUser = defaultMessageToUser;
        LOGGER.info("PostgreSQL Extenal Payload Storage initialized.");
    }

    /**
     * @param operation the type of {@link Operation} to be performed
     * @param payloadType the {@link PayloadType} that is being accessed
     * @return a {@link ExternalStorageLocation} object which contains the pre-signed URL and the
     *     PostgreSQL object key for the json payload
     */
    @Override
    public ExternalStorageLocation getLocation(
            Operation operation, PayloadType payloadType, String path) {

        return getLocationInternal(path, () -> idGenerator.generate() + URI_SUFFIX);
    }

    @Override
    public ExternalStorageLocation getLocation(
            Operation operation, PayloadType payloadType, String path, byte[] payloadBytes) {

        return getLocationInternal(
                path, () -> DigestUtils.sha256Hex(payloadBytes) + URI_SUFFIX_HASHED);
    }

    private ExternalStorageLocation getLocationInternal(
            String path, Supplier<String> calculateKey) {
        ExternalStorageLocation externalStorageLocation = new ExternalStorageLocation();
        String objectKey;
        if (StringUtils.isNotBlank(path)) {
            objectKey = path;
        } else {
            objectKey = calculateKey.get();
        }
        String uri = conductorUrl + URI_PREFIX_EXTERNAL + objectKey;
        externalStorageLocation.setUri(uri);
        externalStorageLocation.setPath(objectKey);
        LOGGER.debug("External storage location URI: {}, location path: {}", uri, objectKey);
        return externalStorageLocation;
    }

    /**
     * Uploads the payload to the given PostgreSQL object key. It is expected that the caller
     * retrieves the object key using {@link #getLocation(Operation, PayloadType, String)} before
     * making this call.
     *
     * @param key the PostgreSQL key of the object to be uploaded
     * @param payload an {@link InputStream} containing the json payload which is to be uploaded
     * @param payloadSize the size of the json payload in bytes
     */
    @Override
    public void upload(String key, InputStream payload, long payloadSize) {
        try (Connection conn = postgresDataSource.getConnection();
                PreparedStatement stmt =
                        conn.prepareStatement(
                                "INSERT INTO "
                                        + tableName
                                        + " (id, data) VALUES (?, ?) ON CONFLICT(id) "
                                        + "DO UPDATE SET created_on=CURRENT_TIMESTAMP")) {
            stmt.setString(1, key);
            stmt.setBinaryStream(2, payload, payloadSize);
            stmt.executeUpdate();
            LOGGER.debug(
                    "External PostgreSQL uploaded key: {}, payload size: {}", key, payloadSize);
        } catch (SQLException e) {
            String msg = "Error uploading data into External PostgreSQL";
            LOGGER.error(msg, e);
            throw new NonTransientException(msg, e);
        }
    }

    /**
     * Downloads the payload stored in the PostgreSQL.
     *
     * @param key the PostgreSQL key of the object
     * @return an input stream containing the contents of the object. Caller is expected to close
     *     the input stream.
     */
    @Override
    public InputStream download(String key) {
        InputStream inputStream;
        try (Connection conn = postgresDataSource.getConnection();
                PreparedStatement stmt =
                        conn.prepareStatement("SELECT data FROM " + tableName + " WHERE id = ?")) {
            stmt.setString(1, key);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    LOGGER.debug("External PostgreSQL data with this ID: {} does not exist", key);
                    return new ByteArrayInputStream(defaultMessageToUser.getBytes());
                }
                inputStream = rs.getBinaryStream(1);
                LOGGER.debug("External PostgreSQL downloaded key: {}", key);
            }
        } catch (SQLException e) {
            String msg = "Error downloading data from external PostgreSQL";
            LOGGER.error(msg, e);
            throw new NonTransientException(msg, e);
        }
        return inputStream;
    }
}
