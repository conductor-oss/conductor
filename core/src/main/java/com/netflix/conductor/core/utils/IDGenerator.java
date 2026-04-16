/*
 * Copyright 2022 Conductor Authors.
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
package com.netflix.conductor.core.utils;

import java.util.UUID;

/**
 * ID Generator used by Conductor. The default ID generator uses UUID v4 as the ID format. By
 * providing a custom IDGenerator bean it is possible to use a different scheme for ID generation.
 * However, this is not normal and should only be done after very careful consideration.
 *
 * <p>Please note, if you use Cassandra persistence, the schema uses UUID as the column type and the
 * IDs have to be valid UUIDs supported by Cassandra.
 */
public class IDGenerator {

    public IDGenerator() {}

    public String generate() {
        return UUID.randomUUID().toString();
    }
}
