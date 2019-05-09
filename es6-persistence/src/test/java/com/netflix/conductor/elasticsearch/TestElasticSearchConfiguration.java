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
}
