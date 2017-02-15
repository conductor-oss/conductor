/**
 * 
 */
package com.netflix.conductor.core.events;

import com.netflix.conductor.core.events.queue.ObservableQueue;

/**
 * @author Viren
 *
 */
public interface EventQueueProvider {

	public ObservableQueue getQueue(String queueURI);
}
