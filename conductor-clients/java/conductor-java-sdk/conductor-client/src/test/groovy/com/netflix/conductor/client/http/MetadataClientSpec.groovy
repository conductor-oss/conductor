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
package com.netflix.conductor.client.http

import com.fasterxml.jackson.core.type.TypeReference
import com.netflix.conductor.client.exception.ConductorClientException
import com.netflix.conductor.common.metadata.workflow.WorkflowDef
import spock.lang.Subject

import static ConductorClientRequest.builder

class MetadataClientSpec extends ClientSpecification {

    @Subject
    MetadataClient metadataClient

    def setup() {
        metadataClient = new MetadataClient(apiClient)
    }

    def "workflow delete"() {
        given:
            def workflowName = 'test'
            int version = 1

        when:
            metadataClient.unregisterWorkflowDef(workflowName, version)

        then:
            1 * apiClient.execute(builder()
                    .method(ConductorClientRequest.Method.DELETE)
                    .path('/metadata/workflow/{name}/{version}')
                    .addPathParam('name', workflowName)
                    .addPathParam("version", version)
                    .build());
    }

    // not sure if this is a meaningful test
    def "workflow delete throws exception"() {
        given:
            def workflowName = 'test'
            int version = 1

        when:
            metadataClient.unregisterWorkflowDef(workflowName, version)

        then:
            1 * apiClient.execute(builder()
                    .method(ConductorClientRequest.Method.DELETE)
                    .path('/metadata/workflow/{name}/{version}')
                    .addPathParam('name', workflowName)
                    .addPathParam("version", version)
                    .build()) >> { throw new ConductorClientException(200, "Error while deleting workflow") }
            def ex = thrown(ConductorClientException.class)
            ex.message == "200: Error while deleting workflow"
    }

    def "workflow delete version missing"() {
        when:
            metadataClient.unregisterWorkflowDef("some name", null)

        then:
            def ex = thrown(NullPointerException.class)
            ex.message == "version cannot be null"
    }

    def "workflow delete name missing"() {
        when:
            metadataClient.unregisterWorkflowDef(null, 1)

        then:
            def ex = thrown(NullPointerException.class)
            ex.message == "Name cannot be blank"

        when:
            metadataClient.unregisterWorkflowDef("   ", 1)

        then:
            ex = thrown(IllegalArgumentException.class)
            ex.message == "Name cannot be blank"
    }

    def "workflow get all definitions latest version"() {
        given:
            List<WorkflowDef> result = new ArrayList<WorkflowDef>()

        when:
           def ret = metadataClient.getAllWorkflowsWithLatestVersions()

        then:
            1 * apiClient.execute(builder()
                    .method(ConductorClientRequest.Method.GET)
                    .path('/metadata/workflow/latest-versions')
                    .build(), _) >> new ConductorClientResponse(200, [:], result)
            ret == result
    }
}
