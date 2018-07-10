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

import com.google.protobuf.Any;
import com.github.vmg.protogen.annotations.*;

import java.util.Map;

@ProtoMessage(toProto = false)
public class SkipTaskRequest {
	@ProtoField(id = 1)
	private Map<String, Object> taskInput;

	@ProtoField(id = 2)
	private Map<String, Object> taskOutput;

	@ProtoField(id = 3)
	private Any taskInputMessage;

	@ProtoField(id = 4)
	private Any taskOutputMessage;

	public Map<String, Object> getTaskInput() {
		return taskInput;
	}

	public void setTaskInput(Map<String, Object> taskInput) {
		this.taskInput = taskInput;
	}

	public Map<String, Object> getTaskOutput() {
		return taskOutput;
	}

	public void setTaskOutput(Map<String, Object> taskOutput) {
		this.taskOutput = taskOutput;
	}

	public Any getTaskInputMessage() {
		return taskInputMessage;
	}

	public void setTaskInputMessage(Any taskInputMessage) {
		this.taskInputMessage = taskInputMessage;
	}

	public Any getTaskOutputMessage() {
		return taskOutputMessage;
	}

	public void setTaskOutputMessage(Any taskOutputMessage) {
		this.taskOutputMessage = taskOutputMessage;
	}
}
