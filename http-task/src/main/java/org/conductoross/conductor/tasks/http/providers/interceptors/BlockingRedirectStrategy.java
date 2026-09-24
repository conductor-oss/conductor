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
import java.net.UnknownHostException;

import org.apache.hc.client5.http.RedirectException;
import org.apache.hc.client5.http.impl.DefaultRedirectStrategy;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Re-applies the {@code HTTP} system task SSRF block rules to every redirect target.
 *
 * <p>{@link RestTemplateInterceptor} only guards the initial request. The underlying Apache
 * HttpClient follows {@code 3xx} redirects transparently, so an allowed host could redirect the
 * request to a blocked internal address (e.g. {@code 169.254.169.254}) and bypass the block list.
 * This strategy resolves and re-checks the target of each redirect through the same {@link
 * HostAndIPChecker} and aborts the exchange when the target is blocked.
 *
 * <p>Extends {@link DefaultRedirectStrategy} so all redirect status codes it recognizes (301, 302,
 * 303, 307, 308) are covered, rather than only 301/302.
 *
 * @see org.conductoross.conductor.tasks.http.config.HttpWorkerBlockConfig
 */
@Component
public class BlockingRedirectStrategy extends DefaultRedirectStrategy {

    private static final Logger LOGGER = LoggerFactory.getLogger(BlockingRedirectStrategy.class);

    private final HostAndIPChecker hostAndIPChecker;

    public BlockingRedirectStrategy(HostAndIPChecker hostAndIPChecker) {
        this.hostAndIPChecker = hostAndIPChecker;
    }

    @Override
    public URI getLocationURI(HttpRequest request, HttpResponse response, HttpContext context)
            throws HttpException {
        final URI location = super.getLocationURI(request, response, context);
        final String host = location.getHost();
        try {
            if (host != null && !hostAndIPChecker.isAllowed(host)) {
                LOGGER.warn("Blocked redirect to host/ip: {}. Redirect URI: {}", host, location);
                throw new RedirectException("Redirect to blocked host/ip: " + host);
            }
        } catch (UnknownHostException e) {
            throw new RedirectException("Unable to resolve redirect host: " + host, e);
        }
        return location;
    }
}
