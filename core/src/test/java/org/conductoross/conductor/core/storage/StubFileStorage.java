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
package org.conductoross.conductor.core.storage;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.conductoross.conductor.model.file.StorageType;

public class StubFileStorage implements FileStorage {

    private final Map<String, byte[]> files = new ConcurrentHashMap<>();

    @Override
    public StorageType getStorageType() {
        return StorageType.LOCAL;
    }

    @Override
    public String generateUploadUrl(String storagePath, Duration expiration) {
        return "stub://upload/" + storagePath;
    }

    @Override
    public String generateDownloadUrl(String storagePath, Duration expiration) {
        return "stub://download/" + storagePath;
    }

    @Override
    public StorageFileInfo getStorageFileInfo(String storagePath) {
        if (!files.containsKey(storagePath)) {
            return null;
        }
        byte[] data = files.get(storagePath);
        StorageFileInfo info = new StorageFileInfo();
        info.setExists(true);
        info.setContentHash(null);
        info.setContentSize(data.length);
        return info;
    }

    @Override
    public String initiateMultipartUpload(String storagePath) {
        return "stub-upload-id";
    }

    @Override
    public String generatePartUploadUrl(
            String storagePath, String uploadId, int partNumber, Duration expiration) {
        return "stub://part/" + storagePath + "/" + partNumber;
    }

    @Override
    public void completeMultipartUpload(
            String storagePath, String uploadId, List<String> partETags) {
        files.putIfAbsent(storagePath, new byte[0]);
    }

    public void putFile(String storagePath, byte[] data) {
        files.put(storagePath, data);
    }
}
