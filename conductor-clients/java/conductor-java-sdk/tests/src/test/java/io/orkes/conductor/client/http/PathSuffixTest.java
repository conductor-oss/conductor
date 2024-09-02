/*
 * Copyright 2022 Orkes, Inc.
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
package io.orkes.conductor.client.http;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.client.http.ConductorClient;

import static org.junit.jupiter.api.Assertions.assertEquals;


public class PathSuffixTest {

    @Test
    @DisplayName("Trailing / should be removed by constructor")
    public void baseConstructor() {
        var client = new ConductorClient("https://play.orkes.io/api");
        var client2 = new ConductorClient("https://play.orkes.io/api/");

        assertEquals("https://play.orkes.io/api", client.getBasePath());
        assertEquals(client2.getBasePath(), client.getBasePath());
    }

    @Test
    @DisplayName("Trailing / should be removed by builder")
    public void test() {
        ConductorClient client = ConductorClient.builder().basePath("https://play.orkes.io/api/")
                        .build();
        assertEquals("https://play.orkes.io/api", client.getBasePath());
    }
}
