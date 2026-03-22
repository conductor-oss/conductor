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
package org.conductoross.conductor;

import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SpaInterceptorTest {

    private final SpaInterceptor spaInterceptor = new SpaInterceptor();

    @Test
    public void testAllowsSwaggerConfigUnderApiDocs() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest(
                        new MockServletContext(), "GET", "/api-docs/swagger-config");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertTrue(spaInterceptor.preHandle(request, response, new Object()));
    }

    @Test
    public void testForwardsSpaRoutesToIndexHtml() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest(new MockServletContext(), "GET", "/workflows");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(spaInterceptor.preHandle(request, response, new Object()));
        assertEquals("/index.html", response.getForwardedUrl());
    }
}
