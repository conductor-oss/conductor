/**
 * Copyright 2017 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/**
 *
 */
package com.netflix.conductor.core.events.nats;

import com.netflix.conductor.contribs.queue.nats.NATSObservableQueue;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.events.EventQueueProvider;
import com.netflix.conductor.core.events.queue.ObservableQueue;
import io.nats.client.ConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Scheduler;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Oleksiy Lysak
 */
@Singleton
public class NATSEventQueueProvider implements EventQueueProvider {
    private static Logger logger = LoggerFactory.getLogger(NATSEventQueueProvider.class);
    protected Map<String, NATSObservableQueue> queues = new ConcurrentHashMap<>();
    private ConnectionFactory factory;
    private final Scheduler scheduler;
    
    @Inject
    public NATSEventQueueProvider(Configuration config, Scheduler scheduler) {
        this.scheduler = scheduler;
        logger.info("NATS Event Queue Provider init");

        // Init NATS API. Handle "io_nats" and "io.nats" ways to specify parameters
        Properties props = new Properties();
        Properties temp = new Properties();
        temp.putAll(System.getenv());
        temp.putAll(System.getProperties());
        temp.forEach((key1, value) -> {
            String key = key1.toString();
            String val = value.toString();
            
            if (key.startsWith("io_nats")) {
                key = key.replace("_", ".");
            }
            props.put(key, config.getProperty(key, val));
        });
        
        // Init NATS API
        factory = new ConnectionFactory(props);
        logger.info("NATS Event Queue Provider initialized...");
    }
    
    @Override
    public ObservableQueue getQueue(String queueURI) {
        NATSObservableQueue queue = queues.computeIfAbsent(queueURI, q -> new NATSObservableQueue(factory, queueURI, scheduler));
        if (queue.isClosed()) {
            queue.open();
        }
        return queue;
    }
}
