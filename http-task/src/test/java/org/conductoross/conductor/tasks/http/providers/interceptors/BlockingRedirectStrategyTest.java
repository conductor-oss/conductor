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

import org.apache.hc.client5.http.RedirectException;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.conductoross.conductor.tasks.http.config.HttpWorkerBlockConfig;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BlockingRedirectStrategyTest {

    private BlockingRedirectStrategy strategy(HttpWorkerBlockConfig config) {
        HostAndIPChecker checker =
                new HostAndIPChecker(new HostCheckerImpl(config), new IPCheckerImpl(config));
        return new BlockingRedirectStrategy(checker);
    }

    private HttpResponse redirectTo(String location) {
        return redirectResponse(302, location);
    }

    private HttpResponse redirectResponse(int code, String location) {
        HttpResponse response = new BasicHttpResponse(code);
        response.setHeader("Location", location);
        return response;
    }

    @Test(expected = RedirectException.class)
    public void blockedRedirectTargetIsRejected() throws Exception {
        HttpWorkerBlockConfig config = new HttpWorkerBlockConfig();
        config.setIps(List.of("169.254.0.0/16"));

        // Allowed host that redirects to the cloud metadata endpoint.
        strategy(config)
                .getLocationURI(
                        new BasicHttpRequest("GET", "http://allowed.example.com/"),
                        redirectTo("http://169.254.169.254/latest/meta-data/"),
                        HttpClientContext.create());
    }

    @Test
    public void allowedRedirectTargetPassesThrough() throws Exception {
        HttpWorkerBlockConfig config = new HttpWorkerBlockConfig();
        config.setIps(List.of("169.254.0.0/16"));

        // Literal public IP avoids real DNS resolution in tests.
        URI location =
                strategy(config)
                        .getLocationURI(
                                new BasicHttpRequest("GET", "http://allowed.example.com/"),
                                redirectTo("http://8.8.8.8/next"),
                                HttpClientContext.create());

        assertEquals(URI.create("http://8.8.8.8/next"), location);
    }

    @Test
    public void recognizesAllRedirectStatusCodes() throws Exception {
        BlockingRedirectStrategy strategy = strategy(new HttpWorkerBlockConfig());
        HttpRequest request = new BasicHttpRequest("GET", "http://allowed.example.com/");
        HttpClientContext context = HttpClientContext.create();

        for (int code : new int[] {301, 302, 303, 307, 308}) {
            assertTrue(
                    "expected " + code + " to be treated as a redirect",
                    strategy.isRedirected(
                            request, redirectResponse(code, "http://8.8.8.8/next"), context));
        }
        assertFalse(
                strategy.isRedirected(
                        request, redirectResponse(200, "http://8.8.8.8/next"), context));
    }
}
