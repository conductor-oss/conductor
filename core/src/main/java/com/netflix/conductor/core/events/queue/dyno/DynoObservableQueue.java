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
package com.netflix.conductor.core.events.queue.dyno;

import com.google.common.annotations.VisibleForTesting;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.events.queue.Message;
import com.netflix.conductor.core.events.queue.ObservableQueue;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.metrics.Monitors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Observable.OnSubscribe;
import rx.Scheduler;
import rx.schedulers.Schedulers;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author Viren
 *
 */
@Singleton
public class DynoObservableQueue implements ObservableQueue {

    private static final Logger logger = LoggerFactory.getLogger(DynoObservableQueue.class);

    private static final String QUEUE_TYPE = "conductor";

    private final String queueName;
    private final QueueDAO queueDAO;
    private final int pollTimeInMS;
    private final int longPollTimeout;
    private final int pollCount;
    private final Scheduler scheduler;

    @Inject
    DynoObservableQueue(String queueName, QueueDAO queueDAO, Configuration config, Scheduler scheduler) {
        this.queueName = queueName;
        this.queueDAO = queueDAO;
        this.pollTimeInMS = config.getIntProperty("workflow.dyno.queues.pollingInterval", 100);
        this.pollCount = config.getIntProperty("workflow.dyno.queues.pollCount", 10);
        this.longPollTimeout = config.getIntProperty("workflow.dyno.queues.longPollTimeout", 1000);
        this.scheduler = scheduler;
    }


    @Override
    public Observable<Message> observe() {
        OnSubscribe<Message> subscriber = getOnSubscribe();
        return Observable.create(subscriber);
    }

    @Override
    public List<String> ack(List<Message> messages) {
        for (Message msg : messages) {
            queueDAO.ack(queueName, msg.getId());
        }
        return messages.stream().map(Message::getId).collect(Collectors.toList());
    }

    public void setUnackTimeout(Message message, long unackTimeout) {
        queueDAO.setUnackTimeout(queueName, message.getId(), unackTimeout);
    }

    @Override
    public void publish(List<Message> messages) {
        queueDAO.push(queueName, messages);
    }

    @Override
    public long size() {
        return queueDAO.getSize(queueName);
    }

    @Override
    public String getType() {
        return QUEUE_TYPE;
    }

    @Override
    public String getName() {
        return queueName;
    }

    @Override
    public String getURI() {
        return queueName;
    }

    @VisibleForTesting
    private List<Message> receiveMessages() {
        try {
            List<Message> messages = queueDAO.pollMessages(queueName, pollCount, longPollTimeout);
            Monitors.recordEventQueueMessagesProcessed(QUEUE_TYPE, queueName, messages.size());
            Monitors.recordEventQueuePollSize(queueName, messages.size());
            return messages;
        } catch (Exception exception) {
            logger.error("Exception while getting messages from  queueDAO", exception);
            Monitors.recordObservableQMessageReceivedErrors(QUEUE_TYPE);
        }
        return new ArrayList<>();
    }

    @VisibleForTesting
    private OnSubscribe<Message> getOnSubscribe() {
        return subscriber -> {
            Observable<Long> interval = Observable.interval(pollTimeInMS, TimeUnit.MILLISECONDS, scheduler);
            interval.flatMap((Long x) -> {
                List<Message> msgs = receiveMessages();
                return Observable.from(msgs);
            }).subscribe(subscriber::onNext, subscriber::onError);
        };
    }
}
