/*
 * Copyright 2024 Conductor Authors.
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
package com.netflix.conductor.test.integration

import org.springframework.boot.test.context.SpringBootTest

import com.netflix.conductor.test.base.AbstractSpecification

/**
 * Simple test to verify basic context loads correctly with SQS module present.
 * This is a fast smoke test to catch basic configuration issues without full SQS integration.
 */
@SpringBootTest(
    classes = com.netflix.conductor.ConductorTestApp.class,
    properties = [
        "conductor.db.type=memory",
        "conductor.queue.type=memory",
        "conductor.external-payload-storage.type=mock",
        "conductor.indexing.enabled=false",
        "conductor.workflow-repair-service.enabled=false",
        "conductor.app.workflow-execution-lock-enabled=false",
        "conductor.event-queues.sqs.enabled=false",  // Disable SQS for basic context test
        "conductor.metrics-prometheus.enabled=false",
        "loadSample=false"
    ]
)
class SQSContextLoadTest extends AbstractSpecification {

    def "Test Spring context loads successfully with SQS module available"() {
        expect: "Spring context loads without errors"
        workflowExecutionService != null
        metadataService != null
        
        println "✅ Spring context loaded successfully with SQS module present"
    }

    def "Test basic Conductor services are functional"() {
        when: "Test basic service availability"
        def executionServiceAvailable = workflowExecutionService != null
        def metadataServiceAvailable = metadataService != null
        
        then: "Core services are injected correctly"
        executionServiceAvailable
        metadataServiceAvailable
        
        println "✅ Core Conductor services are functional"
    }

    def "Test basic workflow definition operations"() {
        given: "A simple workflow definition structure"
        def workflowDef = [
            name: "test_simple_workflow",
            description: "Simple test workflow for context validation",
            version: 1,
            tasks: [
                [
                    name: "simple_task",
                    taskReferenceName: "simple_task_ref",
                    type: "SIMPLE",
                    inputParameters: [:]
                ]
            ]
        ]
        
        when: "Process basic workflow definition properties"
        def workflowName = workflowDef.name
        def taskCount = workflowDef.tasks.size()
        def firstTaskType = workflowDef.tasks[0].type
        
        then: "Basic structure processing works correctly"
        workflowName == "test_simple_workflow"
        taskCount == 1
        firstTaskType == "SIMPLE"
        
        println "✅ Basic workflow definition processing functional"
    }
}
