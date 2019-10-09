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
package com.netflix.conductor.common.utils;

/**
 * @author Manan
 *
 */
public class TaskUtils {

	private static String LOOP_TASK_DELIMITER = "__";

	public static String appendIteration(String name, int iteration) {
		return name + LOOP_TASK_DELIMITER + iteration;
	}

	public static String getLoopOverTaskRefNameSuffix(int iteration) {
		return LOOP_TASK_DELIMITER + iteration;
	}

	public static String removeIterationFromTaskRefName(String referenceTaskName) {
		String[] tokens = referenceTaskName.split(TaskUtils.LOOP_TASK_DELIMITER);
		return tokens.length > 0 ? tokens[0]: referenceTaskName;
	}
}
