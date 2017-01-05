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
package com.netflix.conductor.contribs.queue.sqs;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.auth.policy.Action;
import com.amazonaws.auth.policy.Policy;
import com.amazonaws.auth.policy.Principal;
import com.amazonaws.auth.policy.Resource;
import com.amazonaws.auth.policy.Statement;
import com.amazonaws.auth.policy.Statement.Effect;
import com.amazonaws.auth.policy.actions.SQSActions;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.CreateQueueResult;
import com.amazonaws.services.sqs.model.DeleteMessageBatchRequest;
import com.amazonaws.services.sqs.model.DeleteMessageBatchRequestEntry;
import com.amazonaws.services.sqs.model.DeleteMessageBatchResult;
import com.amazonaws.services.sqs.model.GetQueueAttributesResult;
import com.amazonaws.services.sqs.model.ListQueuesRequest;
import com.amazonaws.services.sqs.model.ListQueuesResult;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.amazonaws.services.sqs.model.SendMessageBatchRequest;
import com.amazonaws.services.sqs.model.SendMessageBatchRequestEntry;
import com.amazonaws.services.sqs.model.SendMessageBatchResult;
import com.amazonaws.services.sqs.model.SetQueueAttributesResult;
import com.google.common.annotations.VisibleForTesting;
import com.netflix.conductor.contribs.queue.Message;
import com.netflix.conductor.contribs.queue.ObservableQueue;

import rx.Observable;
import rx.Observable.OnSubscribe;
import rx.Subscriber;

/**
 * @author Viren
 *
 */
public class SQSObservableQueue implements ObservableQueue {
	
	private static Logger logger = LoggerFactory.getLogger(SQSObservableQueue.class);
	
	public static final String TYPE = "sqs";

	private String queueName;
	
	private int visibilityTimeout;
	
	private int batchSize;

	private AmazonSQSClient client;
	
	private int pollTimeInMS;
	
	private String queueURL;
	
	private SQSObservableQueue(String queueName, AmazonSQSClient client, int visibilityTimeout, int batchSize, int threadPoolSize, int pollTimeInMS, List<String> accountsToAuthorize) {
		this.queueName = queueName;
		this.client = client;
		this.visibilityTimeout = visibilityTimeout;
		this.batchSize = batchSize;
		this.pollTimeInMS = pollTimeInMS;
		this.queueURL = getOrCreateQueue();
		addPolicy(accountsToAuthorize);
	}
	
	@Override
	public Observable<Message> observe() {
		OnSubscribe<Message> subscriber = getOnSubscribe();
		return Observable.create(subscriber);
	}

	@Override
	public List<String> ack(List<Message> messages) {
		return delete(messages);
	}
	
	@Override
	public void publish(List<Message> messages) {
		publishMessages(messages);
	}
	
	@Override
	public long size() {
		GetQueueAttributesResult attributes = client.getQueueAttributes(queueURL, Arrays.asList("ApproximateNumberOfMessages"));
		String sizeAsStr = attributes.getAttributes().get("ApproximateNumberOfMessages");
		try {
			return Long.parseLong(sizeAsStr);
		} catch(Exception e) {
			return -1;
		}
	}
	
	@Override
	public String getType() {
		return TYPE;
	}

	@Override
	public String getName() {
		return queueName;
	}
	
	@Override
	public String getURI() {
		return queueURL;
	}
	
	public static class Builder {
		
		private String queueName;
		
		private int visibilityTimeout = 30;	//seconds
		
		private int batchSize = 5;
		
		private int threadPoolSize = 5;
		
		private int pollTimeInMS = 100;
		
		private AmazonSQSClient client;
		
		private List<String> accountsToAuthorize = new LinkedList<>();
		
		public Builder withQueueName(String queueName) {
			this.queueName = queueName;
			return this;
		}
		
		public Builder withVisibilityTimeout(int visibilityTimeout) {
			this.visibilityTimeout = visibilityTimeout;
			return this;
		}
		
		public Builder withBatchSize(int batchSize) {
			this.batchSize = batchSize;
			return this;
		}
		
		public Builder withClient(AmazonSQSClient client) {
			this.client = client;
			return this;
		}
		
		public Builder withThreadPoolSize(int threadPoolSize) {
			this.threadPoolSize = threadPoolSize;
			return this;
		}
		
		public Builder withPollTimeInMS(int pollTimeInMS) {
			this.pollTimeInMS = pollTimeInMS;
			return this;
		}
		
		public Builder withAccountsToAuthorize(List<String> accountsToAuthorize) {
			this.accountsToAuthorize = accountsToAuthorize;
			return this;
		}
		
		public Builder addAccountToAuthorize(String accountToAuthorize) {
			this.accountsToAuthorize.add(accountToAuthorize);
			return this;
		}
		
		public SQSObservableQueue build() {
			return new SQSObservableQueue(queueName, client, visibilityTimeout, batchSize, threadPoolSize, pollTimeInMS, accountsToAuthorize);
		}
	}
	
	//Private methods
	@VisibleForTesting
	String getOrCreateQueue() {
        List<String> queueUrls = listQueues(queueName);
		if (queueUrls == null || queueUrls.isEmpty()) {			
            CreateQueueRequest createQueueRequest = new CreateQueueRequest().withQueueName(queueName);
            CreateQueueResult result = client.createQueue(createQueueRequest);
            return result.getQueueUrl();
		} else {
            return queueUrls.get(0);
        }
    }
	
	String getQueueARN() {
		GetQueueAttributesResult response = client.getQueueAttributes(queueURL, Arrays.asList("QueueArn"));
		return response.getAttributes().get("QueueArn");
	}
	
	private void addPolicy(List<String> accountsToAuthorize) {
		logger.info("Authorizing " + accountsToAuthorize + " to the queue " + queueName);
		Map<String, String> attributes = new HashMap<>();
		attributes.put("Policy", getPolicy(accountsToAuthorize));
		SetQueueAttributesResult result = client.setQueueAttributes(queueURL, attributes);
		logger.info("policy attachment result: " + result);
		logger.info("policy attachment result: status=" + result.getSdkHttpMetadata().getHttpStatusCode());
	}
	
	private String getPolicy(List<String> accountIds) {
		Policy policy = new Policy("AuthorizedWorkerAccessPolicy");
		Statement stmt = new Statement(Effect.Allow);
		Action action = SQSActions.SendMessage;
		stmt.getActions().add(action);
		stmt.setResources(new LinkedList<>());
		for(String accountId : accountIds) {
			Principal principal = new Principal(accountId);
			stmt.getPrincipals().add(principal);
		}
		stmt.getResources().add(new Resource(getQueueARN()));
		policy.getStatements().add(stmt);
		return policy.toJson();
	}

	private List<String> listQueues(String queueName) {
        ListQueuesRequest listQueuesRequest = new ListQueuesRequest().withQueueNamePrefix(queueName);
        ListQueuesResult resultList = client.listQueues(listQueuesRequest);
        List<String> queueUrls = resultList.getQueueUrls().stream().filter(queueUrl -> queueUrl.contains(queueName)).collect(Collectors.toList());
        return queueUrls;
    }
	
	void publishMessages(List<Message> messages) {
		logger.info("Sending {} messages", messages.size());
		SendMessageBatchRequest batch = new SendMessageBatchRequest(queueURL);
		messages.stream().forEach(msg -> {
			SendMessageBatchRequestEntry sendr = new SendMessageBatchRequestEntry(msg.getId(), msg.getPayload());
			batch.getEntries().add(sendr);
		});
		logger.info("sending {}", batch.getEntries().size());
		SendMessageBatchResult result = client.sendMessageBatch(batch);
		logger.info("send result {}", result.getFailed().toString());
	}
	
	@VisibleForTesting
	List<Message> receiveMessages() {
        ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest()
                .withQueueUrl(queueURL)
                .withVisibilityTimeout(visibilityTimeout)
                .withMaxNumberOfMessages(batchSize);
        ReceiveMessageResult result = client.receiveMessage(receiveMessageRequest);
        return result.getMessages().stream().map(msg -> new Message(msg.getMessageId(), msg.getBody(), msg.getReceiptHandle())).collect(Collectors.toList());
    }
	
	@VisibleForTesting
	OnSubscribe<Message> getOnSubscribe() {
		OnSubscribe<Message> subscriber = new Observable.OnSubscribe<Message>() {
			@Override
			public void call(Subscriber<? super Message> subscriber) {
				Observable<Long> interval = Observable.interval(pollTimeInMS, TimeUnit.MILLISECONDS);		
				interval.flatMap((Long x)->{
					List<Message> msgs = receiveMessages();
		            return Observable.from(msgs);
				}).subscribe((Message msg)->{
					subscriber.onNext(msg);
				}, (Throwable exception)->{
					subscriber.onError(exception);
				});
			}
		};
		return subscriber;
	}
	
	private List<String> delete(List<Message> messages) {
		if (messages == null || messages.isEmpty()) {
            return null;
        }

        DeleteMessageBatchRequest batch = new DeleteMessageBatchRequest().withQueueUrl(queueURL);
    	List<DeleteMessageBatchRequestEntry> entries = batch.getEntries();

    	messages.stream().forEach(m -> entries.add(new DeleteMessageBatchRequestEntry().withId(m.getId()).withReceiptHandle(m.getReceipt())));
        
        DeleteMessageBatchResult result = client.deleteMessageBatch(batch);
        List<String> failures = result.getFailed().stream().map(fm -> fm.getId()).collect(Collectors.toList());
		logger.debug("failed to delete: {}", failures);
        return failures;

    }
	
}
