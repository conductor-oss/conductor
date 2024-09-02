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


import com.netflix.conductor.common.run.SearchResult
import com.netflix.conductor.common.run.TaskSummary
import spock.lang.Subject

import static ConductorClientRequest.builder

class TaskClientSpec extends ClientSpecification {

    @Subject
    TaskClient taskClient

    def setup() {
        taskClient = new TaskClient(apiClient)
    }

    //FIXME is going to fail with UnsupportedOperationException:"search operation on tasks is not supported"
//    def search() {
//        given:
//            def query = 'my_complex_query'
//            def result = new SearchResult<>()
//            result.totalHits = 1
//            result.results = [new TaskSummary()]
//
//        when:
//            def searchResult = taskClient.search(query)
//
//        then:
//            1 * apiClient.doRequest(builder()
//                    .method(OrkesHttpClientRequest.Method.GET)
//                    .path('/tasks/search')
//                    .addQueryParam('query', query)
//                    .build(), _) >> new ApiResponse(200, [:], result)
//
//            searchResult.totalHits == result.totalHits
//            searchResult.results && searchResult.results.size() == 1
//            searchResult.results[0] instanceof TaskSummary
//    }

    //FIXME is going to fail with UnsupportedOperationException:"search operation on tasks is not supported"
//    def searchV2() {
//        given:
//            def query = 'my_complex_query'
//            def result = new SearchResult<>()
//            result.totalHits = 1
//            result.results = [new Task()]
//
//        when:
//            def searchResult = taskClient.searchV2('my_complex_query')
//
//        then:
//            1 * apiClient.doRequest(builder()
//                    .method(OrkesHttpClientRequest.Method.GET)
//                    .path('/tasks/searchV2')
//                    .addQueryParam('query', query)
//                    .build(), _) >> new ApiResponse(200, [:], result)
//
//            searchResult.totalHits == result.totalHits
//            searchResult.results && searchResult.results.size() == 1
//            searchResult.results[0] instanceof Task
//    }

    def "search with params"() {
        given:
            def query = 'my_complex_query'
            int start = 0
            int size = 10
            def sort = 'sort'
            def freeText = 'text'
            def result = new SearchResult<>()
            result.totalHits = 1
            result.results = [new TaskSummary()]

        when:
            def searchResult = taskClient.search(start, size, sort, freeText, query)

        then:
            1 * apiClient.execute(builder()
                    .method(ConductorClientRequest.Method.GET)
                    .path("/tasks/search")
                    .addQueryParam("start", start)
                    .addQueryParam("size", size)
                    .addQueryParam("sort", sort)
                    .addQueryParam("freeText", freeText)
                    .addQueryParam("query", query)
                    .build(), _) >> new ConductorClientResponse(200, [:], result)

            searchResult.totalHits == result.totalHits
            searchResult.results && searchResult.results.size() == 1
            searchResult.results[0] instanceof TaskSummary
    }

    //FIXME is going to fail with UnsupportedOperationException:"search operation on tasks is not supported"
//    def "searchV2 with params"() {
//        given:
//            def query = 'my_complex_query'
//            int start = 0
//            int size = 10
//            def sort = 'sort'
//            def freeText = 'text'
//            def result = new SearchResult<>()
//            result.totalHits = 1
//            result.results = [new Task()]
//
//        when:
//            def searchResult = taskClient.searchV2(start, size, sort, freeText, query)
//
//        then:
//            1 * apiClient.doRequest(builder()
//                    .method(OrkesHttpClientRequest.Method.GET)
//                    .path("/tasks/searchv2")
//                    .addQueryParam("start", start)
//                    .addQueryParam("size", size)
//                    .addQueryParam("sort", sort)
//                    .addQueryParam("freeText", freeText)
//                    .addQueryParam("query", query)
//                    .build(), _) >> new ApiResponse(200, [:], result)
//
//            searchResult.totalHits == result.totalHits
//            searchResult.results && searchResult.results.size() == 1
//            searchResult.results[0] instanceof TaskSummary
//    }
}
