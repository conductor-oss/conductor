/**
 * 
 */
package com.netflix.conductor.client.worker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.Test;

/**
 * @author Viren
 *
 */
public class TestPropertyFactory {

	@BeforeClass
	public static void init() {
		
		//Polling interval for all the workers is 2 second
		System.setProperty("conductor.worker.pollingInterval", "2");
		
		System.setProperty("conductor.worker.paused", "false");
		System.setProperty("conductor.worker.workerA.paused", "true");
		
		System.setProperty("conductor.worker.workerB.batchSize", "84");
	}
	
	@Test
	public void test() {
		
		assertEquals(2, PropertyFactory.getInteger("workerB", "pollingInterval", 100).intValue());
		assertEquals(100, PropertyFactory.getInteger("workerB", "propWithoutValue", 100).intValue());
		
		assertFalse(PropertyFactory.getBoolean("workerB", "paused", true));		//Global value set to 'false'
		assertTrue(PropertyFactory.getBoolean("workerA", "paused", false));		//WorkerA value set to 'true'
		
		
		assertEquals(42, PropertyFactory.getInteger("workerA", "batchSize", 42).intValue());	//No global value set, so will return the default value supplied
		assertEquals(84, PropertyFactory.getInteger("workerB", "batchSize", 42).intValue());	//WorkerB's value set to 84
		

	}
	
}
