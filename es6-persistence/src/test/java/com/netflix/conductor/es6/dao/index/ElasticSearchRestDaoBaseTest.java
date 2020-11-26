package com.netflix.conductor.es6.dao.index;

import org.apache.http.HttpHost;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.junit.After;
import org.junit.Before;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

abstract class ElasticSearchRestDaoBaseTest extends ElasticSearchTest {

    protected RestClient restClient;
    protected ElasticSearchRestDAOV6 indexDAO;

    @Before
    public void setup() throws Exception {
        String httpHostAddress = container.getHttpHostAddress();
        String host = httpHostAddress.split(":")[0];
        int port = Integer.parseInt(httpHostAddress.split(":")[1]);

        properties.setURL("http://" + httpHostAddress);

        RestClientBuilder restClientBuilder = RestClient.builder(new HttpHost(host, port, "http"));
        restClient = restClientBuilder.build();

        indexDAO = new ElasticSearchRestDAOV6(restClientBuilder, properties, objectMapper);
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
        Response beforeResponse = restClient.performRequest("GET", "/_cat/indices");

        Reader streamReader = new InputStreamReader(beforeResponse.getEntity().getContent());
        BufferedReader bufferedReader = new BufferedReader(streamReader);

        String line;
        while ((line = bufferedReader.readLine()) != null) {
            String[] fields = line.split("\\s");
            String endpoint = String.format("/%s", fields[2]);

            restClient.performRequest("DELETE", endpoint);
        }
    }
}
