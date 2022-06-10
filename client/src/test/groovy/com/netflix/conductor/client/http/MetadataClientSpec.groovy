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
import com.netflix.conductor.client.exception.RequestHandlerException

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
        1 * requestHandler.delete(uri)
    }

    def "workflow delete throws exception"() {
        given:
        String workflowName = 'test'
        int version = 1
        InputStream errorResponse = toInputStream"""
            {
              "status": 404,
              "message": "No such workflow definition: $workflowName version: $version",
              "instance": "conductor-server",
              "retryable": false
            }
        """
        URI uri = createURI("metadata/workflow/$workflowName/$version")

        when:
        metadataClient.unregisterWorkflowDef(workflowName, version)

        then:
        1 * requestHandler.delete(uri) >> { throw new RequestHandlerException(errorResponse, 404) }
        def ex = thrown(ConductorClientException.class)
        ex && ex.status == 404
        ex.message == "No such workflow definition: $workflowName version: $version"
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
        thrown(IllegalArgumentException.class)
    }
}
