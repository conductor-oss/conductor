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

import com.netflix.conductor.common.metadata.events.EventHandler

import com.sun.jersey.api.client.ClientResponse
import com.sun.jersey.api.client.WebResource
import spock.lang.Subject
import spock.lang.Unroll

class EventClientSpec extends ClientSpecification {

    @Subject
    EventClient eventClient

    def setup() {
        eventClient = new EventClient(requestHandler)
        eventClient.setRootURI(ROOT_URL)
    }

    def "register event handler"() {
        given:
        EventHandler handler = new EventHandler()
        URI uri = createURI("event")

        when:
        eventClient.registerEventHandler(handler)

        then:
        1 * requestHandler.getWebResourceBuilder(uri, handler) >> Mock(WebResource.Builder.class)
    }

    def "update event handler"() {
        given:
        EventHandler handler = new EventHandler()
        URI uri = createURI("event")

        when:
        eventClient.updateEventHandler(handler)

        then:
        1 * requestHandler.getWebResourceBuilder(uri, handler) >> Mock(WebResource.Builder.class)
    }

    def "unregister event handler"() {
        given:
        String eventName = "test"
        URI uri = createURI("event/$eventName")

        when:
        eventClient.unregisterEventHandler(eventName)

        then:
        1 * requestHandler.delete(uri, null)
    }

    @Unroll
    def "get event handlers activeOnly=#activeOnly"() {
        given:
        def handlers = [new EventHandler(), new EventHandler()]
        String eventName = "test"
        URI uri = createURI("event/$eventName?activeOnly=$activeOnly")

        when:
        def eventHandlers = eventClient.getEventHandlers(eventName, activeOnly)

        then:
        eventHandlers && eventHandlers.size() == 2
        1 * requestHandler.get(uri) >> Mock(ClientResponse.class) {
            getEntity(_) >> handlers
        }

        where:
        activeOnly << [true, false]
    }

}
