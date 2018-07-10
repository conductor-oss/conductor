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
package com.netflix.conductor.common.metadata.workflow;

import com.github.vmg.protogen.annotations.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@ProtoMessage
public class DynamicForkJoinTaskList {

    @ProtoField(id = 1)
	private List<DynamicForkJoinTask> dynamicTasks = new ArrayList<DynamicForkJoinTask>();
	
	public void add(String taskName, String workflowName, String referenceName, Map<String, Object> input){
		dynamicTasks.add(new DynamicForkJoinTask(taskName, workflowName, referenceName, input));
	}

	public void add(DynamicForkJoinTask dtask){
		dynamicTasks.add(dtask);
	}
	
	public List<DynamicForkJoinTask> getDynamicTasks() {
		return dynamicTasks;
	}

	public void setDynamicTasks(List<DynamicForkJoinTask> dynamicTasks) {
		this.dynamicTasks = dynamicTasks;
	}

}
