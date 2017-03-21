/**
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/**
 * 
 */
package com.netflix.conductor.client.task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.worker.Worker;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;

/**
 * @author Viren
 *
 */
public class WorkflowTaskCoordinatorTests {

	@Test
	public void testLoggingEnvironment() {
		Worker worker = Worker.create("test", (Task task)-> new TaskResult(task));
		List<String> keys = worker.getLoggingEnvProps();
		
		Map<String, Object> env = WorkflowTaskCoordinator.getEnvData(worker);
		assertNotNull(env);
		assertTrue(!env.isEmpty());
		Set<String> loggedKeys = env.keySet();
		for(String key : keys) {
			assertTrue(loggedKeys.contains(key));
		}
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testNoWorkersException() {
		new WorkflowTaskCoordinator.Builder().build();
	}
	
	@Test
	public void testThreadPool() {
		
		Worker worker = Worker.create("test", (Task task)-> new TaskResult(task));
		WorkflowTaskCoordinator coordinator = new WorkflowTaskCoordinator.Builder().withWorkers(worker, worker, worker).withTaskClient(new TaskClient()).build();
		assertEquals(-1, coordinator.getThreadCount());		//Not initialized yet
		coordinator.init();
		assertEquals(3, coordinator.getThreadCount());
		assertEquals(100, coordinator.getWorkerQueueSize());		//100 is the default value
		assertEquals(500, coordinator.getSleepWhenRetry());
		assertEquals(3, coordinator.getUpdateRetryCount());
		
		
		coordinator = new WorkflowTaskCoordinator.Builder()
				.withWorkers(worker)
				.withThreadCount(100)
				.withWorkerQueueSize(400)
				.withSleepWhenRetry(100)
				.withUpdateRetryCount(10)
				.withTaskClient(new TaskClient())
				.build();
		assertEquals(100, coordinator.getThreadCount());
		coordinator.init();
		assertEquals(100, coordinator.getThreadCount());
		assertEquals(400, coordinator.getWorkerQueueSize());
		assertEquals(100, coordinator.getSleepWhenRetry());
		assertEquals(10, coordinator.getUpdateRetryCount());
		
		
	
	}
}
