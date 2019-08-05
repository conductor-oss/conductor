package com.netflix.conductor.contribs.queue;

import com.google.common.annotations.VisibleForTesting;
import com.netflix.conductor.core.events.queue.Message;
import com.netflix.conductor.core.events.queue.ObservableQueue;
import rx.Observable;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Created at 22/03/2019 17:37
 *
 * @author Mickaël GREGORI <mickael.gregori@alchimie.com>
 * @version $Id$
 */
public abstract class AbstractObservableQueue implements ObservableQueue {

    protected int pollTimeInMS;

    public int getPollTimeInMS() {
        return pollTimeInMS;
    }

    protected abstract List<Message> receiveMessages();

    @VisibleForTesting
    public Observable.OnSubscribe<Message> getOnSubscribe() {
        return subscriber -> {
            Observable<Long> interval = Observable.interval(pollTimeInMS, TimeUnit.MILLISECONDS);
            interval.flatMap((Long x)->{
                List<Message> msgs = receiveMessages();
                return Observable.from(msgs);
            }).subscribe(subscriber::onNext, subscriber::onError);
        };
    }
}
