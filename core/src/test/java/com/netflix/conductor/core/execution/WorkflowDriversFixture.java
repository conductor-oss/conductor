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
package com.netflix.conductor.core.execution;

import java.util.Map;

import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.dal.ExecutionDAOFacade;
import com.netflix.conductor.core.execution.mapper.TaskMapper;
import com.netflix.conductor.core.execution.tasks.SystemTaskRegistry;
import com.netflix.conductor.core.metadata.MetadataMapperService;
import com.netflix.conductor.core.utils.ExternalPayloadStorageUtils;
import com.netflix.conductor.core.utils.ParametersUtils;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.dao.QueueDAO;

import tools.jackson.databind.ObjectMapper;

import static org.mockito.Mockito.mock;

/** Builds the drivers for tests that create the decider, the executor or the sweeper by hand. */
public final class WorkflowDriversFixture {

    private WorkflowDriversFixture() {}

    /** For tests that run standard workflows only and do not look at the output or the queues. */
    public static WorkflowDrivers standardOnly() {
        return standardOnly(mock(ExecutionDAOFacade.class), mock(QueueDAO.class));
    }

    /** For tests that run standard workflows only, with the stores of the test. */
    public static WorkflowDrivers standardOnly(
            ExecutionDAOFacade executionDAOFacade, QueueDAO queueDAO) {
        return new WorkflowDrivers(
                new StandardDriver(
                        mock(ParametersUtils.class),
                        mock(ExternalPayloadStorageUtils.class),
                        executionDAOFacade,
                        queueDAO),
                mock(NativeDriver.class));
    }

    public static WorkflowDrivers drivers(
            ParametersUtils parametersUtils,
            ExternalPayloadStorageUtils externalPayloadStorageUtils,
            NativeContinuationService nativeContinuationService,
            MetadataDAO metadataDAO,
            ExecutionDAOFacade executionDAOFacade,
            QueueDAO queueDAO,
            SystemTaskRegistry systemTaskRegistry) {
        StandardDriver standard =
                new StandardDriver(
                        parametersUtils, externalPayloadStorageUtils, executionDAOFacade, queueDAO);
        return new WorkflowDrivers(
                standard,
                new NativeDriver(
                        standard,
                        nativeContinuationService,
                        new NativeRerunPlanner(),
                        metadataDAO,
                        new MetadataMapperService(metadataDAO),
                        executionDAOFacade,
                        queueDAO,
                        systemTaskRegistry));
    }

    public static NativeContinuationService nativeContinuationService(
            ObjectMapper objectMapper,
            ConductorProperties properties,
            MetadataDAO metadataDAO,
            Map<String, TaskMapper> taskMappers) {
        return new NativeContinuationService(
                objectMapper,
                properties,
                new NativeFragmentValidator(
                        objectMapper,
                        properties,
                        new MetadataMapperService(metadataDAO),
                        taskMappers));
    }
}
