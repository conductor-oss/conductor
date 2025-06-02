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

import jakarta.ws.rs.client.Invocation
import jakarta.ws.rs.core.Response
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
        Invocation.Builder builder = Mock(Invocation.Builder.class)
        Response response = Mock(Response.class)


        when:
        eventClient.registerEventHandler(handler)

        then:
        1 * requestHandler.getWebResourceBuilder(uri) >> builder
        1 * builder.post(_) >> response
        1 * response.getStatus() >> 200
    }

    def "update event handler"() {
        given:
        EventHandler handler = new EventHandler()
        URI uri = createURI("event")
        Invocation.Builder builder = Mock(Invocation.Builder.class)
        Response response = Mock(Response.class)

        when:
        eventClient.updateEventHandler(handler)

        then:
        1 * requestHandler.getWebResourceBuilder(uri) >> builder
        1 * builder.put(_) >> response
        1 * response.getStatus() >> 200
    }

    def "unregister event handler"() {
        given:
        String eventName = "test"
        URI uri = createURI("event/$eventName")
        Response response = Mock(Response.class)

        when:
        eventClient.unregisterEventHandler(eventName)

        then:
        1 * requestHandler.delete(uri) >> response
        1 * response.getStatus() >> 200
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
        1 * requestHandler.get(uri) >> Mock(Response.class) {
            readEntity(_) >> handlers
        }

        where:
        activeOnly << [true, false]
    }

}
