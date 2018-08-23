/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.conductor.tests.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.common.run.ExternalStorageLocation;
import com.netflix.conductor.common.utils.ExternalPayloadStorage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class MockExternalPayloadStorage implements ExternalPayloadStorage {

    private ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public ExternalStorageLocation getLocation(Operation operation, PayloadType payloadType) {
        return null;
    }

    @Override
    public void upload(String path, InputStream payload, long payloadSize) {
    }

    @Override
    public InputStream download(String path) {
        try {

            Map<String, Object> payload = getPayload(path);
            String jsonString = objectMapper.writeValueAsString(payload);
            return new ByteArrayInputStream(jsonString.getBytes());
        } catch (IOException e) {
            return null;
        }
    }

    private Map<String, Object> getPayload(String path) {
        Map<String, Object> stringObjectMap = new HashMap<>();
        switch (path) {
            case "workflow/input":
                stringObjectMap.put("param1", "p1 value");
                stringObjectMap.put("param2", "p2 value");
                break;
            case "task/output":
                stringObjectMap.put("op", "success_task1");
                break;
        }
        return stringObjectMap;
    }
}
