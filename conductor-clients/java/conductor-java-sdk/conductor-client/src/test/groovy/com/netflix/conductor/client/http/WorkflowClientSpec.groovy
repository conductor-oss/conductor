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
import com.netflix.conductor.common.run.WorkflowSummary
import spock.lang.Subject

import static ConductorClientRequest.Method
import static ConductorClientRequest.builder

class WorkflowClientSpec extends ClientSpecification {

    @Subject
    WorkflowClient workflowClient

    def setup() {
        workflowClient = new WorkflowClient(apiClient)
    }

    def search() {
        given:
            def query = 'my_complex_query'
            def result = new SearchResult<>()
            result.totalHits = 1
            result.results = [new WorkflowSummary()]

        when:
            def searchResult = workflowClient.search(query)

        then:
            //FIXME the order of the query params matter in the test. This is NOT good.
            1 * apiClient.execute(builder()
                    .method(Method.GET)
                    .path("/workflow/search")
                    .addQueryParam("start", null as String)
                    .addQueryParam("size", null as String)
                    .addQueryParam("sort", null as String)
                    .addQueryParam("freeText", "") //FIXME should this be null?
                    .addQueryParam("query", query)
                    .addQueryParam("skipCache", null as Boolean)
                    .build(), _) >> new ConductorClientResponse(200, [:], result)

            searchResult.totalHits == result.totalHits
            searchResult.results && searchResult.results.size() == 1
            searchResult.results[0] instanceof WorkflowSummary
    }

    //FIXME is going to fail with UnsupportedOperationException:"Please use search() API"
    def searchV2() {
//        given:
//            def query = 'my_complex_query'
//            def result = new SearchResult<>()
//            result.totalHits = 1
//            result.results = [new Workflow(workflowDefinition: new WorkflowDef(), createTime: System.currentTimeMillis())]
//
//        when:
//            def searchResult = workflowClient.searchV2('my_complex_query')
//
//        then:
//            //FIXME the order of the query params matter in the test. This is NOT good.
//            1 * apiClient.doRequest(builder()
//                    .method(Method.GET)
//                    .path("/workflow/search-v2")
//                    .addQueryParam("start", null as String)
//                    .addQueryParam("size", null as String)
//                    .addQueryParam("sort", null as String)
//                    .addQueryParam("freeText", "") //FIXME should this be null?
//                    .addQueryParam("query", query)
//                    .addQueryParam("skipCache", null as Boolean)
//                    .build(), _) >> new ApiResponse(200, [:], result)
//
//            searchResult.totalHits == result.totalHits
//            searchResult.results && searchResult.results.size() == 1
//            searchResult.results[0] instanceof Workflow
    }

    def "search with params"() {
        given:
            def query = 'my_complex_query'
            def start = 0
            def size = 10
            def sort = 'sort'
            def freeText = 'text'
            def result = new SearchResult<>()
            result.totalHits = 1
            result.results = [new WorkflowSummary()]
        when:
            def searchResult = workflowClient.search(start, size, sort, freeText, query)

        then:
            1 * apiClient.execute(builder()
                    .method(Method.GET)
                    .path("/workflow/search")
                    .addQueryParam("start", start)
                    .addQueryParam("size", size)
                    .addQueryParam("sort", sort)
                    .addQueryParam("freeText", freeText)
                    .addQueryParam("query", query)
                    .addQueryParam("skipCache", null as Boolean)
                    .build(), _) >> new ConductorClientResponse(200, [:], result)

            searchResult.totalHits == result.totalHits
            searchResult.results && searchResult.results.size() == 1
            searchResult.results[0] instanceof WorkflowSummary
    }

    //FIXME is going to fail with UnsupportedOperationException:"Please use search() API"
    def "searchV2 with params"() {
//        given:
//            def query = 'my_complex_query'
//            def start = 0
//            def size = 10
//            def sort = 'sort'
//            def freeText = 'text'
//            def result = new SearchResult<>()
//            result.totalHits = 1
//            result.results = [new Workflow(workflowDefinition: new WorkflowDef(), createTime: System.currentTimeMillis())]
//
//        when:
//            def searchResult = workflowClient.searchV2(start, size, sort, freeText, query)
//
//        then:
//            1 * apiClient.doRequest(builder()
//                    .method(Method.GET)
//                    .path("/workflow/search-v2")
//                    .addQueryParam("start", start)
//                    .addQueryParam("size", size)
//                    .addQueryParam("sort", sort)
//                    .addQueryParam("freeText", freeText)
//                    .addQueryParam("query", query)
//                    .addQueryParam("skipCache", null as Boolean)
//                    .build(), _) >> new ApiResponse(200, [:], result)
//
//            searchResult.totalHits == result.totalHits
//            searchResult.results && searchResult.results.size() == 1
//            searchResult.results[0] instanceof Workflow
    }
}
