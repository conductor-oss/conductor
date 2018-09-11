/*
 * Copyright 2016 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.conductor.core.execution;

import java.util.HashMap;
import java.util.Map;

public enum Code {
    INVALID_INPUT(400), INTERNAL_ERROR(500), NOT_FOUND(404), CONFLICT(409), UNAUTHORIZED(403), BACKEND_ERROR(500);

    private final int statusCode;

    Code(int statusCode){
        this.statusCode = statusCode;
    }

    public int getStatusCode(){
        return statusCode;
    }

    private static final Map<Integer, Code> codesByValue = new HashMap<>();

    static {
        for (Code type : Code.values()) {
            codesByValue.put(type.statusCode, type);
        }
    }

    public static Code forValue(int value) {
        return codesByValue.get(value);
    }
}