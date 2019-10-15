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
package com.netflix.conductor.core.execution;

import java.util.HashSet;
import java.util.Set;

import com.netflix.conductor.core.execution.tasks.Decision;
import com.netflix.conductor.core.execution.tasks.ExclusiveJoin;
import com.netflix.conductor.core.execution.tasks.Fork;
import com.netflix.conductor.core.execution.tasks.Join;
import com.netflix.conductor.core.execution.tasks.DoWhile;
import com.netflix.conductor.core.execution.tasks.WorkflowSystemTask;
/**
 * Defines a system task type
 * 
 *
 */
public enum SystemTaskType {

	DECISION(new Decision()), FORK(new Fork()), JOIN(new Join()), EXCLUSIVE_JOIN(new ExclusiveJoin()), DO_WHILE(new DoWhile());
	
	private static Set<String> builtInTasks = new HashSet<>();
	static {

		builtInTasks.add(SystemTaskType.DECISION.name());
		builtInTasks.add(SystemTaskType.FORK.name());
		builtInTasks.add(SystemTaskType.JOIN.name());
		builtInTasks.add(SystemTaskType.EXCLUSIVE_JOIN.name());
	}

	private WorkflowSystemTask impl;
	
	SystemTaskType(WorkflowSystemTask impl) {
		this.impl = impl;
	}
	
	public WorkflowSystemTask impl() {
		return this.impl;
	}

	public static boolean is(String taskType) {
		return WorkflowSystemTask.is(taskType);
	}
		
	public static boolean isBuiltIn(String taskType) {
		return is(taskType) && builtInTasks.contains(taskType);		
	}

}