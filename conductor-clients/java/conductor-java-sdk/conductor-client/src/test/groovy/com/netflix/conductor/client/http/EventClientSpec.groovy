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

import com.netflix.conductor.common.metadata.events.EventHandler
import spock.lang.Subject
import spock.lang.Unroll

import static ConductorClientRequest.Method
import static ConductorClientRequest.builder

class EventClientSpec extends ClientSpecification {

    @Subject
    EventClient eventClient

    def setup() {
        eventClient = new EventClient(apiClient)
    }

    def "register event handler"() {
        given:
            def handler = new EventHandler()

        when:
            eventClient.registerEventHandler(handler)

        then:
            1 * apiClient.execute(builder()
                    .method(Method.POST)
                    .body(handler)
                    .path("/event")
                    .build()
            ) >> new ConductorClientResponse(200, [:])
    }

    def "update event handler"() {
        given:
            def handler = new EventHandler()

        when:
            eventClient.updateEventHandler(handler)

        then:
            1 * apiClient.execute(builder()
                    .method(Method.PUT)
                    .body(handler)
                    .path("/event")
                    .build()
            ) >> new ConductorClientResponse(200, [:])
    }

    def "unregister event handler"() {
        given:
            def eventName = "test"

        when:
            eventClient.unregisterEventHandler(eventName)

        then:
            1 * apiClient.execute(builder()
                    .method(Method.DELETE)
                    .path('/event/{name}')
                    .addPathParam('name', eventName)
                    .build());
    }

    @Unroll
    def "get event handlers activeOnly=#activeOnly"() {
        given:
            def handlers = [new EventHandler(), new EventHandler()]
            def eventName = "test"

        when:
            def eventHandlers = eventClient.getEventHandlers(eventName, activeOnly)

        then:
            eventHandlers && eventHandlers.size() == 2

            1 * apiClient.execute(builder()
                    .method(Method.GET)
                    .path('/event/{name}')
                    .addPathParam('name', eventName)
                    .addQueryParam('activeOnly', activeOnly)
                    .build(), _) >> new ConductorClientResponse(200, [:], handlers)

        where:
            activeOnly << [true, false]
    }

}
