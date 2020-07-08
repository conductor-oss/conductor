/*
 * Copyright 2020 Netflix, Inc.
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
package com.netflix.conductor.contribs;

import com.netflix.conductor.contribs.listener.ArchivingWithTTLWorkflowStatusListener;
import com.netflix.conductor.contribs.listener.ArchivingWorkflowStatusListener;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.execution.WorkflowStatusListener;
import com.netflix.conductor.core.orchestration.ExecutionDAOFacade;

import javax.inject.Inject;
import javax.inject.Provider;

public class ArchiveWorkflowStatusListenerProvider implements Provider<WorkflowStatusListener> {

    private final ExecutionDAOFacade executionDAOFacade;
    private final Configuration config;

    @Inject
    ArchiveWorkflowStatusListenerProvider(ExecutionDAOFacade executionDAOFacade, Configuration config) {
        this.executionDAOFacade = executionDAOFacade;
        this.config = config;
    }

    @Override
    public WorkflowStatusListener get() {
        if (config.getWorkflowArchivalTTL() > 0) {
            return new ArchivingWithTTLWorkflowStatusListener(executionDAOFacade, config);
        } else {
            return new ArchivingWorkflowStatusListener(executionDAOFacade);
        }
    }
}
