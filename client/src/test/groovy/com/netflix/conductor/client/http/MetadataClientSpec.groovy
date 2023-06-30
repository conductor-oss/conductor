/*
 * Copyright 2022 Netflix, Inc.
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

import com.netflix.conductor.client.exception.ConductorClientException
import com.netflix.conductor.common.metadata.workflow.WorkflowDef

import com.sun.jersey.api.client.ClientResponse
import spock.lang.Subject

class MetadataClientSpec extends ClientSpecification {

    @Subject
    MetadataClient metadataClient

    def setup() {
        metadataClient = new MetadataClient(requestHandler)
        metadataClient.setRootURI(ROOT_URL)
    }

    def "workflow delete"() {
        given:
        String workflowName = 'test'
        int version = 1
        URI uri = createURI("metadata/workflow/$workflowName/$version")

        when:
        metadataClient.unregisterWorkflowDef(workflowName, version)

        then:
        1 * requestHandler.delete(uri, null)
    }

    def "workflow delete throws exception"() {
        given:
        String workflowName = 'test'
        int version = 1
        URI uri = createURI("metadata/workflow/$workflowName/$version")

        when:
        metadataClient.unregisterWorkflowDef(workflowName, version)

        then:
        1 * requestHandler.delete(uri, null) >> { throw new RuntimeException(clientResponse) }
        def ex = thrown(ConductorClientException.class)
        ex.message == "Unable to invoke Conductor API with uri: $uri, runtime exception occurred"
    }

    def "workflow delete version missing"() {
        when:
        metadataClient.unregisterWorkflowDef("some name", null)

        then:
        thrown(NullPointerException.class)
    }

    def "workflow delete name missing"() {
        when:
        metadataClient.unregisterWorkflowDef(null, 1)

        then:
        thrown(NullPointerException.class)

        when:
        metadataClient.unregisterWorkflowDef("   ", 1)

        then:
        thrown(IllegalArgumentException.class)
    }

    def "workflow get all definitions latest version"() {
        given:
        List<WorkflowDef> result = new ArrayList<WorkflowDef>()
        URI uri = createURI("metadata/workflow/latest-versions")

        when:
        metadataClient.getAllWorkflowsWithLatestVersions()

        then:
        1 * requestHandler.get(uri) >>  Mock(ClientResponse.class) {
            getEntity(_) >> result
        }
    }
}
