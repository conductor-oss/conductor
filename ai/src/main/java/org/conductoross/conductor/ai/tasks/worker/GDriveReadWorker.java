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
package org.conductoross.conductor.ai.tasks.worker;

import org.conductoross.conductor.common.integrations.gdrive.GDriveIntegrationService;
import org.conductoross.conductor.common.integrations.gdrive.GDriveLoadRequest;
import org.conductoross.conductor.common.integrations.gdrive.GDriveLoadResponse;
import org.conductoross.conductor.core.execution.tasks.AnnotatedSystemTaskWorker;
import org.springframework.stereotype.Component;

import com.netflix.conductor.sdk.workflow.task.WorkerTask;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class GDriveReadWorker implements AnnotatedSystemTaskWorker {

    public static final String NAME = "GDRIVE_READ";

    private final GDriveIntegrationService gDriveIntegrationService;

    public GDriveReadWorker(GDriveIntegrationService gDriveIntegrationService) {
        this.gDriveIntegrationService = gDriveIntegrationService;
    }

    @WorkerTask(NAME)
    public GDriveLoadResponse read(GDriveLoadRequest request) {
        log.debug("Loading Google Drive folder metadata for GDRIVE_READ");
        return gDriveIntegrationService.loadFolder(request);
    }
}
