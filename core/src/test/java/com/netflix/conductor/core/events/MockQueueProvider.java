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
package com.netflix.conductor.core.events;

import com.netflix.conductor.core.events.queue.ObservableQueue;

/**
 * @author Viren
 *
 */
public class MockQueueProvider implements EventQueueProvider {

	private final String type;
	
	public MockQueueProvider(String type) {
		this.type = type;
	}
	
	@Override
	public ObservableQueue getQueue(String queueURI) {
		return new MockObservableQueue(queueURI, queueURI, type);
	}
}
