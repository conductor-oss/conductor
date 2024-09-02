/*
 * Copyright 2022 Orkes, Inc.
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
package com.netflix.conductor.sdk.workflow.utils;

import java.util.HashMap;
import java.util.Map;

public class MapBuilder {
    private final Map<String, Object> map = new HashMap<>();

    public MapBuilder add(String key, String value) {
        map.put(key, value);
        return this;
    }

    public MapBuilder add(String key, Number value) {
        map.put(key, value);
        return this;
    }

    public MapBuilder add(String key, MapBuilder value) {
        map.put(key, value.build());
        return this;
    }

    public Map<String, Object> build() {
        return map;
    }
}
