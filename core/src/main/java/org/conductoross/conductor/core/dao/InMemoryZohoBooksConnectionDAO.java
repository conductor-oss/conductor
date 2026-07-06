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
package org.conductoross.conductor.core.dao;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.conductoross.conductor.common.integrations.zoho.ZohoBooksConnection;
import org.conductoross.conductor.dao.ZohoBooksConnectionDAO;

public class InMemoryZohoBooksConnectionDAO implements ZohoBooksConnectionDAO {

    private final ConcurrentMap<String, ZohoBooksConnection> connections =
            new ConcurrentHashMap<>();

    @Override
    public void saveConnection(ZohoBooksConnection connection) {
        long now = Instant.now().toEpochMilli();
        connections.compute(
                connection.getConnectionId(),
                (connectionId, existing) -> {
                    ZohoBooksConnection stored = copy(connection);
                    stored.setCreatedAt(existing == null ? now : existing.getCreatedAt());
                    stored.setUpdatedAt(now);
                    return stored;
                });
    }

    @Override
    public ZohoBooksConnection getConnection(String connectionId) {
        return copy(connections.get(connectionId));
    }

    @Override
    public List<ZohoBooksConnection> getAllConnections() {
        List<ZohoBooksConnection> result = new ArrayList<>();
        connections.values().forEach(connection -> result.add(copy(connection)));
        return result;
    }

    @Override
    public void deleteConnection(String connectionId) {
        connections.remove(connectionId);
    }

    private ZohoBooksConnection copy(ZohoBooksConnection connection) {
        if (connection == null) {
            return null;
        }
        ZohoBooksConnection copy = new ZohoBooksConnection();
        copy.setConnectionId(connection.getConnectionId());
        copy.setClientId(connection.getClientId());
        copy.setClientSecret(connection.getClientSecret());
        copy.setOrganizationId(connection.getOrganizationId());
        copy.setCreatedAt(connection.getCreatedAt());
        copy.setUpdatedAt(connection.getUpdatedAt());
        return copy;
    }
}
