/*
 * Copyright 2016 Netflix, Inc.
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
package com.netflix.conductor.elasticsearch;

import org.junit.Assert;
import org.junit.Test;

public class TestElasticSearchConfiguration {

	@Test
	public void testAsyncWorkerQueueSize() {
		ElasticSearchConfiguration es = new SystemPropertiesElasticSearchConfiguration();
		int workerQueueSize = es.getAsyncWorkerQueueSize();
		Assert.assertEquals(workerQueueSize, 100);
	}

	@Test
	public void testAsyncMaxPoolSize() {
		ElasticSearchConfiguration es = new SystemPropertiesElasticSearchConfiguration();
		int poolSize = es.getAsyncMaxPoolSize();
		Assert.assertEquals(poolSize, 12);
	}
}
