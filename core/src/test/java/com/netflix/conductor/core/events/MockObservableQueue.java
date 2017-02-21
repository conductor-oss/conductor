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

import java.util.List;

import com.netflix.conductor.core.events.queue.Message;
import com.netflix.conductor.core.events.queue.ObservableQueue;

import rx.Observable;

/**
 * @author Viren
 *
 */
public class MockObservableQueue implements ObservableQueue {

	public MockObservableQueue() {
		
	}
	
	@Override
	public Observable<Message> observe() {
		return null;
	}

	public String getType() {
		return null;
	}

	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getURI() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<String> ack(List<Message> messages) {
		return null;
	}

	@Override
	public void publish(List<Message> messages) {
		// TODO Auto-generated method stub

	}

	@Override
	public long size() {
		return 0;
	}

}
