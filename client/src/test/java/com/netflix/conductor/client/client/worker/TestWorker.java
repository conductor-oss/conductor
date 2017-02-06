/**
 * Copyright 2016 Netflix, Inc.
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
package com.netflix.conductor.client.client.worker;

import static org.junit.Assert.*;

import org.junit.BeforeClass;
import org.junit.Test;

import com.netflix.conductor.client.worker.Worker;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;

/**
 * @author Viren
 *
 */
public class TestWorker {

	@BeforeClass
	public static void init() {
		System.setProperty("conductor.worker.Test.paused", "true");
	}
	
	@Test
	public void testIdentity(){
		Worker worker = Worker.create("Test2", (Task task)->{
			return new TaskResult(task);
		});
		assertNotNull(worker.getIdentity());
		boolean paused = worker.paused();
		assertFalse("Paused? " + paused, paused);
	}
	
	@Test
	public void testProperty() {
		Worker worker = Worker.create("Test", (Task task)->{
			return new TaskResult(task);
		});
		boolean paused = worker.paused();
		assertTrue("Paused? " + paused, paused);
	}
}
