/*
 * Copyright 2020 Netflix, Inc.
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
package com.netflix.conductor.contribs.tasks.http;

import org.junit.Test;
import org.springframework.web.client.RestTemplate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

public class RestTemplateProviderTest {

    @Test
    public void differentObjectsForDifferentThreads() throws InterruptedException {
        RestTemplateProvider restTemplateProvider = new RestTemplateProvider(150, 100);
        final RestTemplate restTemplate = restTemplateProvider.getRestTemplate(new HttpTask.Input());
        final StringBuilder result = new StringBuilder();
        Thread t1 = new Thread(() -> {
            RestTemplate restTemplate1 = restTemplateProvider.getRestTemplate(new HttpTask.Input());
            if (restTemplate1 != restTemplate) {
                result.append("different");
            }
        });
        t1.start();
        t1.join();
        assertEquals(result.toString(), "different");
    }

    @Test
    public void sameObjectForSameThread() {
        RestTemplateProvider restTemplateProvider = new RestTemplateProvider(150, 100);
        RestTemplate client1 = restTemplateProvider.getRestTemplate(new HttpTask.Input());
        RestTemplate client2 = restTemplateProvider.getRestTemplate(new HttpTask.Input());
        assertSame(client1, client2);
        assertNotNull(client1);
    }
}
