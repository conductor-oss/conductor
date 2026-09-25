/*
 * Copyright 2025 Conductor Authors.
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
package org.conductoross.conductor.tasks.http.providers.interceptors;

import java.net.URI;
import java.util.List;

import org.conductoross.conductor.tasks.http.config.HttpWorkerBlockConfig;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RestTemplateInterceptorTest {

    private RestTemplateInterceptor interceptor(HttpWorkerBlockConfig config) {
        HostAndIPChecker checker =
                new HostAndIPChecker(new HostCheckerImpl(config), new IPCheckerImpl(config));
        return new RestTemplateInterceptor(checker);
    }

    private HttpRequest requestTo(String uri) {
        HttpRequest request = Mockito.mock(HttpRequest.class);
        when(request.getURI()).thenReturn(URI.create(uri));
        return request;
    }

    @Test
    public void blockedHostReturns403AndDoesNotExecute() throws Exception {
        HttpWorkerBlockConfig config = new HttpWorkerBlockConfig();
        config.setIps(List.of("169.254.0.0/16"));
        ClientHttpRequestExecution execution = Mockito.mock(ClientHttpRequestExecution.class);

        ClientHttpResponse response =
                interceptor(config)
                        .intercept(
                                requestTo("http://169.254.169.254/latest/meta-data/"),
                                new byte[0],
                                execution);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verify(execution, never()).execute(any(), any());
    }

    @Test
    public void allowedHostProceeds() throws Exception {
        HttpWorkerBlockConfig config = new HttpWorkerBlockConfig();
        config.setIps(List.of("169.254.0.0/16"));
        ClientHttpRequestExecution execution = Mockito.mock(ClientHttpRequestExecution.class);
        ClientHttpResponse expected = Mockito.mock(ClientHttpResponse.class);
        when(execution.execute(any(), any())).thenReturn(expected);

        // Literal public IP avoids real DNS resolution in tests.
        ClientHttpResponse response =
                interceptor(config).intercept(requestTo("http://8.8.8.8/"), new byte[0], execution);

        assertEquals(expected, response);
        verify(execution, times(1)).execute(any(), any());
    }
}
