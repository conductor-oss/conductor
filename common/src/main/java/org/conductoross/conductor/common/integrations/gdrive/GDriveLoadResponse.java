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
package org.conductoross.conductor.common.integrations.gdrive;

import java.util.ArrayList;
import java.util.List;

public class GDriveLoadResponse {

    private String folderId;
    private int count;
    private List<GDriveFile> files = new ArrayList<>();

    public GDriveLoadResponse() {}

    public GDriveLoadResponse(String folderId, List<GDriveFile> files) {
        this.folderId = folderId;
        this.files = files;
        this.count = files.size();
    }

    public String getFolderId() {
        return folderId;
    }

    public void setFolderId(String folderId) {
        this.folderId = folderId;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public List<GDriveFile> getFiles() {
        return files;
    }

    public void setFiles(List<GDriveFile> files) {
        this.files = files;
        this.count = files == null ? 0 : files.size();
    }
}
