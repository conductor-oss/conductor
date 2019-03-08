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

package com.netflix.conductor.tests.integration.model;

import java.util.List;

import com.netflix.conductor.common.metadata.tasks.TaskDef;

public class TaskWrapper {

	private List<TaskDef> taskDefs;

	public List<TaskDef> getTaskDefs() {
		return taskDefs;
	}

	public void setTaskDefs(List<TaskDef> taskDefs) {
		this.taskDefs = taskDefs;
	}

	@Override
	public String toString() {
		return "TaskWrapper{" + "taskDefs=" + taskDefs + '}';
	}
}