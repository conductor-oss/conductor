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
package com.netflix.conductor.core.execution.tasks;

import static org.junit.Assert.*;

import org.junit.Test;

import com.netflix.conductor.core.execution.SystemTaskType;

/**
 * @author Viren
 *
 */
public class TestSystemTasks {

	@Test
	public void test(){
		new SubWorkflow();
		assertTrue(SystemTaskType.is(SystemTaskType.JOIN.name()));
		assertTrue(SystemTaskType.is(SystemTaskType.FORK.name()));
		assertTrue(SystemTaskType.is(SystemTaskType.DECISION.name()));
		assertTrue(SystemTaskType.is(SubWorkflow.NAME));
	}

}
