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
package com.netflix.conductor.common.tasks;

import static org.junit.Assert.*;

import org.junit.Test;

import com.netflix.conductor.common.metadata.tasks.TaskDef;

/**
 * @author Viren
 *
 */
public class TestTaskDef {

	@Test
	public void test() {
		
		String name = "test1";
		String description = "desc";
		int retryCount = 10;
		int timeout = 100;
		TaskDef def = new TaskDef(name, description, retryCount, timeout);
		assertEquals(36_00, def.getResponseTimeoutSeconds());
		assertEquals(name, def.getName());
		assertEquals(description, def.getDescription());
		assertEquals(retryCount, def.getRetryCount());
		assertEquals(timeout, def.getTimeoutSeconds());
		
	}
}
