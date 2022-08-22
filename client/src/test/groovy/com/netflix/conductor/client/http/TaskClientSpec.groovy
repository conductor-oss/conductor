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

import com.netflix.conductor.common.metadata.tasks.Task
import com.netflix.conductor.common.run.SearchResult
import com.netflix.conductor.common.run.TaskSummary

import com.sun.jersey.api.client.ClientResponse
import spock.lang.Subject

class TaskClientSpec extends ClientSpecification {

    @Subject
    TaskClient taskClient

    def setup() {
        taskClient = new TaskClient(requestHandler)
        taskClient.setRootURI(ROOT_URL)
    }

    def "search"() {
        given:
        String query = 'my_complex_query'
        SearchResult<TaskSummary> result = new SearchResult<>()
        result.totalHits = 1
        result.results = [new TaskSummary()]

        URI uri = createURI("tasks/search?query=$query")

        when:
        SearchResult<TaskSummary> searchResult = taskClient.search(query)

        then:
        1 * requestHandler.get(uri) >>  Mock(ClientResponse.class) {
            getEntity(_) >> result
        }

        searchResult.totalHits == result.totalHits
        searchResult.results && searchResult.results.size() == 1
        searchResult.results[0] instanceof TaskSummary
    }

    def "searchV2"() {
        given:
        String query = 'my_complex_query'
        SearchResult<Task> result = new SearchResult<>()
        result.totalHits = 1
        result.results = [new Task()]

        URI uri = createURI("tasks/search-v2?query=$query")

        when:
        SearchResult<Task> searchResult = taskClient.searchV2('my_complex_query')

        then:
        1 * requestHandler.get(uri) >> Mock(ClientResponse.class) {
            getEntity(_) >> result
        }

        searchResult.totalHits == result.totalHits
        searchResult.results && searchResult.results.size() == 1
        searchResult.results[0] instanceof Task
    }

    def "search with params"() {
        given:
        String query = 'my_complex_query'
        int start = 0
        int size = 10
        String sort = 'sort'
        String freeText = 'text'
        SearchResult<TaskSummary> result = new SearchResult<>()
        result.totalHits = 1
        result.results = [new TaskSummary()]

        URI uri = createURI("tasks/search?start=$start&size=$size&sort=$sort&freeText=$freeText&query=$query")

        when:
        SearchResult<TaskSummary> searchResult = taskClient.search(start, size, sort, freeText, query)

        then:
        1 * requestHandler.get(uri) >> Mock(ClientResponse.class) {
            getEntity(_) >> result
        }

        searchResult.totalHits == result.totalHits
        searchResult.results && searchResult.results.size() == 1
        searchResult.results[0] instanceof TaskSummary
    }

    def "searchV2 with params"() {
        given:
        String query = 'my_complex_query'
        int start = 0
        int size = 10
        String sort = 'sort'
        String freeText = 'text'
        SearchResult<Task> result = new SearchResult<>()
        result.totalHits = 1
        result.results = [new Task()]

        URI uri = createURI("tasks/search-v2?start=$start&size=$size&sort=$sort&freeText=$freeText&query=$query")

        when:
        SearchResult<Task> searchResult = taskClient.searchV2(start, size, sort, freeText, query)

        then:
        1 * requestHandler.get(uri) >> Mock(ClientResponse.class) {
            getEntity(_) >> result
        }

        searchResult.totalHits == result.totalHits
        searchResult.results && searchResult.results.size() == 1
        searchResult.results[0] instanceof Task
    }
}
