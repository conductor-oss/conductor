/**
 * Copyright 2016 Netflix, Inc.
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
package com.netflix.conductor.contribs.queue.nats;

import com.netflix.conductor.core.events.queue.Message;
import io.nats.client.NUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * @author Oleksiy Lysak
 *
 */
public abstract class NATSAbstractQueue  {
    private static Logger logger = LoggerFactory.getLogger(NATSAbstractQueue.class);
    protected LinkedBlockingQueue<Message> messages = new LinkedBlockingQueue<>();
    String queueURI;
    String subject;
    String queue;

    NATSAbstractQueue(String queueURI) {
        this.queueURI = queueURI;
        // If queue specified (e.g. subject:queue) - split to subject & queue
        if (queueURI.contains(":")) {
            this.subject = queueURI.substring(0, queueURI.indexOf(':'));
            this.queue = queueURI.substring(queueURI.indexOf(':') + 1);
        } else {
            this.subject = queueURI;
            this.queue = null;
        }
        logger.info(String.format("Initialized with queueURI=%s, subject=%s, queue=%s", queueURI, subject, queue));
    }

    Observable<Message> getOnSubscribe() {
        Observable.OnSubscribe<Message> subscribe = subscriber -> {
            Observable<Long> interval = Observable.interval(100, TimeUnit.MILLISECONDS);
            interval.flatMap((Long x) -> {
                List<Message> available = new LinkedList<>();
                messages.drainTo(available);
                return Observable.from(available);
            }).subscribe(subscriber::onNext, subscriber::onError);
        };
        return Observable.create(subscribe);
    }

    void handleOnMessage(String subject, byte[] data, String trace) {
        String payload = new String(data);

        Message dstMsg = new Message();
        dstMsg.setId(NUID.nextGlobal());
        dstMsg.setPayload(payload);

        logger.info(String.format("Received message for %s: %s", subject, payload));
        messages.add(dstMsg);
    }

    void publish(List<Message> messages) {
        messages.forEach(message -> {
            try {
                String payload = message.getPayload();
                publish(subject, payload.getBytes());
                logger.info(String.format("Published message to %s: %s", subject, payload));
            } catch (IOException e) {
                logger.error("Failed to publish message " + message, e);
            }
        });
    }

    abstract void publish(String subject, byte[] data) throws IOException;
}
