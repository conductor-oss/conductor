/*
 * Copyright 2023 Conductor Authors.
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
package com.netflix.conductor.os.dao.index;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;

import org.apache.http.HttpHost;
import org.junit.After;
import org.junit.Before;
import org.opensearch.client.Request;
import org.opensearch.client.Response;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestClientBuilder;
import org.springframework.retry.support.RetryTemplate;

public abstract class OpenSearchRestDaoBaseTest extends OpenSearchTest {

    protected RestClient restClient;
    protected OpenSearchRestDAO indexDAO;

    @Before
    public void setup() throws Exception {
        String httpHostAddress = container.getHttpHostAddress();
        String host = httpHostAddress.split(":")[1].replace("//", "");
        int port = Integer.parseInt(httpHostAddress.split(":")[2]);

        properties.setUrl(httpHostAddress);

        RestClientBuilder restClientBuilder = RestClient.builder(new HttpHost(host, port, "http"));
        restClient = restClientBuilder.build();

        indexDAO =
                new OpenSearchRestDAO(
                        restClientBuilder, new RetryTemplate(), properties, objectMapper);
        indexDAO.setup();
    }

    @After
    public void tearDown() throws Exception {
        deleteAllIndices();

        if (restClient != null) {
            restClient.close();
        }
    }

    private void deleteAllIndices() throws IOException {
        Response beforeResponse = restClient.performRequest(new Request("GET", "/_cat/indices"));
        Reader streamReader = new InputStreamReader(beforeResponse.getEntity().getContent());
        BufferedReader bufferedReader = new BufferedReader(streamReader);

        String line;
        while ((line = bufferedReader.readLine()) != null) {
            System.out.println("Deleting line: " + line);
            String[] fields = line.split("(\\s+)");
            String endpoint = String.format("/%s", fields[2]);
            System.out.println("Deleting index: " + endpoint);
            restClient.performRequest(new Request("DELETE", endpoint));
        }
    }
}
