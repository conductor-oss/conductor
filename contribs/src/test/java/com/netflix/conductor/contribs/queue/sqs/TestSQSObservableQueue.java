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
package com.netflix.conductor.contribs.queue.sqs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.google.common.util.concurrent.Uninterruptibles;
import com.netflix.conductor.contribs.queue.sqs.SQSObservableQueue;
import com.netflix.conductor.core.events.queue.Message;

import rx.Observable;

/**
 * @author Viren
 *
 */
public class TestSQSObservableQueue {

    @Test
    public void test() {

        List<Message> messages = new LinkedList<>();
        Observable.range(0, 10).forEach((Integer x) -> messages.add(new Message("" + x, "payload: " + x, null)));
        assertEquals(10, messages.size());

        SQSObservableQueue queue = mock(SQSObservableQueue.class);
        when(queue.getOrCreateQueue()).thenReturn("junit_queue_url");
        Answer<?> answer = (Answer<List<Message>>) invocation -> Collections.emptyList();
        when(queue.receiveMessages()).thenReturn(messages).thenAnswer(answer);
        when(queue.getOnSubscribe()).thenCallRealMethod();
        when(queue.observe()).thenCallRealMethod();

        List<Message> found = new LinkedList<>();
        Observable<Message> observable = queue.observe();
        assertNotNull(observable);
        observable.subscribe(found::add);

        Uninterruptibles.sleepUninterruptibly(1000, TimeUnit.MILLISECONDS);

        assertEquals(messages.size(), found.size());
        assertEquals(messages, found);
    }

    @Test
    public void testException() {
        SQSObservableQueue queue = mock(SQSObservableQueue.class);
        when(queue.getOrCreateQueue()).thenReturn("junit_queue_url");
        when(queue.receiveMessages()).thenThrow(new RuntimeException("Error in SQS communication"));
        when(queue.getOnSubscribe()).thenCallRealMethod();
        when(queue.observe()).thenCallRealMethod();

        List<Message> found = new LinkedList<>();
        Observable<Message> observable = queue.observe();
        assertNotNull(observable);
        observable.subscribe(found::add);

        Uninterruptibles.sleepUninterruptibly(1000, TimeUnit.MILLISECONDS);

        assertEquals(0, found.size());

    }
}
