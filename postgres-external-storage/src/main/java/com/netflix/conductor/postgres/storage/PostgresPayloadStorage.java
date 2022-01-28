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

import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.common.run.ExternalStorageLocation;
import com.netflix.conductor.common.utils.ExternalPayloadStorage;
import com.netflix.conductor.core.exception.ApplicationException;
import com.netflix.conductor.core.utils.IDGenerator;
import com.netflix.conductor.postgres.config.PostgresPayloadProperties;

/**
 * Store and pull the external payload which consists of key and stream of data in PostgreSQL
 * database
 */
public class PostgresPayloadStorage implements ExternalPayloadStorage {

    private static final Logger LOGGER = LoggerFactory.getLogger(PostgresPayloadStorage.class);

    private final DataSource postgresDataSource;
    private final String tableName;
    private final String conductorUrl;

    public PostgresPayloadStorage(PostgresPayloadProperties properties, DataSource dataSource) {
        tableName = properties.getTableName();
        conductorUrl = properties.getConductorUrl();
        this.postgresDataSource = dataSource;
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

        ExternalStorageLocation externalStorageLocation = new ExternalStorageLocation();
        String objectKey;
        if (StringUtils.isNotBlank(path)) {
            objectKey = path;
        } else {
            objectKey = IDGenerator.generate() + ".json";
        }
        String uri = conductorUrl + "/api/external/postgres/" + objectKey;
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
                        conn.prepareStatement("INSERT INTO " + tableName + " VALUES (?, ?)")) {
            stmt.setString(1, key);
            stmt.setBinaryStream(2, payload, payloadSize);
            stmt.executeUpdate();
            LOGGER.debug(
                    "External PostgreSQL uploaded key: {}, payload size: {}", key, payloadSize);
        } catch (SQLException e) {
            String msg = "Error uploading data into External PostgreSQL";
            LOGGER.error(msg, e);
            throw new ApplicationException(ApplicationException.Code.BACKEND_ERROR, msg, e);
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
            ResultSet rs = stmt.executeQuery();
            rs.next();
            inputStream = rs.getBinaryStream(1);
            rs.close();
            LOGGER.debug("External PostgreSQL downloaded key: {}", key);
        } catch (SQLException e) {
            String msg = "Error downloading data from external PostgreSQL";
            LOGGER.error(msg, e);
            throw new ApplicationException(ApplicationException.Code.BACKEND_ERROR, msg, e);
        }
        return inputStream;
    }
}
