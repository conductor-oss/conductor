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
package com.netflix.conductor.es6.config;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ElasticSearchPropertiesTest {

    @Test
    public void testWaitForIndexRefreshDefaultsToFalse() {
        ElasticSearchProperties properties = new ElasticSearchProperties();
        assertFalse(
                "waitForIndexRefresh should default to false for v3.21.19 performance",
                properties.isWaitForIndexRefresh());
    }

    @Test
    public void testWaitForIndexRefreshCanBeEnabled() {
        ElasticSearchProperties properties = new ElasticSearchProperties();
        properties.setWaitForIndexRefresh(true);
        assertTrue(
                "waitForIndexRefresh should be configurable to true",
                properties.isWaitForIndexRefresh());
    }
}
