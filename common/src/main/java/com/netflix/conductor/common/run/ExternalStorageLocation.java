/*
 * Copyright 2020 Netflix, Inc.
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
package com.netflix.conductor.common.run;

/**
 * Describes the location where the JSON payload is stored in external storage.
 *
 * <p>The location is described using the following fields:
 *
 * <ul>
 *   <li>uri: The uri of the json file in external storage.
 *   <li>path: The relative path of the file in external storage.
 * </ul>
 */
public class ExternalStorageLocation {

    private String uri;
    private String path;

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    @Override
    public String toString() {
        return "ExternalStorageLocation{" + "uri='" + uri + '\'' + ", path='" + path + '\'' + '}';
    }
}
