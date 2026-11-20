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
package com.netflix.conductor.tasks.http.providers;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;
import org.springframework.web.client.RestTemplate;

import com.netflix.conductor.tasks.http.HttpTask;

import static org.junit.Assert.*;

public class DefaultRestTemplateProviderTest {

    private final DefaultRestTemplateProvider provider =
            new DefaultRestTemplateProvider(Duration.ofMillis(150), Duration.ofMillis(100));

    @Test
    public void sameObjectForSameTimeouts() {
        RestTemplate client1 = provider.getRestTemplate(new HttpTask.Input());
        RestTemplate client2 = provider.getRestTemplate(new HttpTask.Input());
        assertNotNull(client1);
        assertSame(client1, client2);
    }

    @Test
    public void sameObjectAcrossThreads() throws InterruptedException {
        RestTemplate restTemplate = provider.getRestTemplate(new HttpTask.Input());
        AtomicReference<RestTemplate> fromOtherThread = new AtomicReference<>();
        Thread t1 =
                new Thread(
                        () -> fromOtherThread.set(provider.getRestTemplate(new HttpTask.Input())));
        t1.start();
        t1.join();
        assertSame(restTemplate, fromOtherThread.get());
    }

    @Test
    public void differentObjectsForDifferentTimeouts() {
        HttpTask.Input slow = new HttpTask.Input();
        slow.setReadTimeOut(10_000);
        HttpTask.Input slowConnect = new HttpTask.Input();
        slowConnect.setConnectionTimeOut(5_000);

        RestTemplate defaults = provider.getRestTemplate(new HttpTask.Input());
        assertNotSame(defaults, provider.getRestTemplate(slow));
        assertNotSame(defaults, provider.getRestTemplate(slowConnect));
        assertSame(provider.getRestTemplate(slow), provider.getRestTemplate(slow));
    }
}
