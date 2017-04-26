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
package com.netflix.conductor.core.config;

import com.google.inject.AbstractModule;
import com.netflix.conductor.core.events.ActionProcessor;
import com.netflix.conductor.core.events.EventProcessor;
import com.netflix.conductor.core.events.queue.dyno.DynoEventQueueProvider;
import com.netflix.conductor.core.execution.tasks.Event;
import com.netflix.conductor.core.execution.tasks.SubWorkflow;
import com.netflix.conductor.core.execution.tasks.SystemTaskWorkerCoordinator;
import com.netflix.conductor.core.execution.tasks.Wait;


/**
 * @author Viren
 *
 */
public class CoreModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(DynoEventQueueProvider.class).asEagerSingleton();
		bind(ActionProcessor.class).asEagerSingleton();
		bind(EventProcessor.class).asEagerSingleton();		
		bind(SystemTaskWorkerCoordinator.class).asEagerSingleton();
		bind(SubWorkflow.class).asEagerSingleton();
		bind(Wait.class).asEagerSingleton();
		bind(Event.class).asEagerSingleton();
	}
	
}
