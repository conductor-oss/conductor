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
