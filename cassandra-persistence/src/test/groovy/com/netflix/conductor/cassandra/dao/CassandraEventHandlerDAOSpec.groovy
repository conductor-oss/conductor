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
package com.netflix.conductor.cassandra.dao

import com.netflix.conductor.common.metadata.events.EventExecution
import com.netflix.conductor.common.metadata.events.EventHandler

import spock.lang.Subject

class CassandraEventHandlerDAOSpec extends CassandraSpec {

    @Subject
    CassandraEventHandlerDAO eventHandlerDAO

    CassandraExecutionDAO executionDAO

    def setup() {
        eventHandlerDAO = new CassandraEventHandlerDAO(session, objectMapper, cassandraProperties, statements)
        executionDAO = new CassandraExecutionDAO(session, objectMapper, cassandraProperties, statements)
    }

    def testEventHandlerCRUD() {
        given:
        String event = "event"
        String eventHandlerName1 = "event_handler1"
        String eventHandlerName2 = "event_handler2"

        EventHandler eventHandler = new EventHandler()
        eventHandler.setName(eventHandlerName1)
        eventHandler.setEvent(event)

        when: // create event handler
        eventHandlerDAO.addEventHandler(eventHandler)
        List<EventHandler> handlers = eventHandlerDAO.getEventHandlersForEvent(event, false)

        then: // fetch all event handlers for event
        handlers != null && handlers.size() == 1
        eventHandler.name == handlers[0].name
        eventHandler.event == handlers[0].event
        !handlers[0].active

        and: // add an active event handler for the same event
        EventHandler eventHandler1 = new EventHandler()
        eventHandler1.setName(eventHandlerName2)
        eventHandler1.setEvent(event)
        eventHandler1.setActive(true)
        eventHandlerDAO.addEventHandler(eventHandler1)

        when: // fetch all event handlers
        handlers = eventHandlerDAO.getAllEventHandlers()

        then:
        handlers != null && handlers.size() == 2

        when: // fetch all event handlers for event
        handlers = eventHandlerDAO.getEventHandlersForEvent(event, false)

        then:
        handlers != null && handlers.size() == 2

        when: // fetch only active handlers for event
        handlers = eventHandlerDAO.getEventHandlersForEvent(event, true)

        then:
        handlers != null && handlers.size() == 1
        eventHandler1.name == handlers[0].name
        eventHandler1.event == handlers[0].event
        handlers[0].active

        when: // remove event handler
        eventHandlerDAO.removeEventHandler(eventHandlerName1)
        handlers = eventHandlerDAO.getAllEventHandlers()

        then:
        handlers != null && handlers.size() == 1
    }



    private static EventExecution getEventExecution(String id, String msgId, String name, String event) {
        EventExecution eventExecution = new EventExecution(id, msgId);
        eventExecution.setName(name);
        eventExecution.setEvent(event);
        eventExecution.setStatus(EventExecution.Status.IN_PROGRESS);
        return eventExecution;
    }
}
