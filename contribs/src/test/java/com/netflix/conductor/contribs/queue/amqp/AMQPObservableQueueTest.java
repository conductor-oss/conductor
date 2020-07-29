package com.netflix.conductor.contribs.queue.amqp;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.internal.stubbing.answers.DoesNothing;
import org.mockito.internal.stubbing.answers.ReturnsArgumentAt;
import org.mockito.internal.stubbing.answers.ReturnsElementsOf;
import org.mockito.stubbing.Answer;
import org.mockito.stubbing.OngoingStubbing;

import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.events.queue.Message;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Address;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.impl.AMQImpl;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import rx.Observable;
import rx.observers.Subscribers;
import rx.observers.TestSubscriber;

public class AMQPObservableQueueTest {

	final int batchSize = 10;
	final int pollTimeMs = 500;

	Address[] addresses;
	Configuration configuration;

	@Before
	public void setUp() {
		configuration = Mockito.mock(Configuration.class);
		Answer answer = new ReturnsArgumentAt(1);
		when(configuration.getProperty(Mockito.anyString(), Mockito.anyString())).thenAnswer(answer);
		when(configuration.getBooleanProperty(Mockito.anyString(), Mockito.anyBoolean())).thenAnswer(answer);
		when(configuration.getIntProperty(Mockito.anyString(), anyInt())).thenAnswer(answer);
		addresses = new Address[] { new Address("localhost", AMQP.PROTOCOL.PORT) };
	}

	List<GetResponse> buildQueue(final Random random, final int bound) {
		final LinkedList<GetResponse> queue = new LinkedList();
		for (int i = 0; i < bound; i++) {
			AMQP.BasicProperties props = Mockito.mock(AMQP.BasicProperties.class);
			Mockito.when(props.getMessageId()).thenReturn(UUID.randomUUID().toString());
			Envelope envelope = Mockito.mock(Envelope.class);
			Mockito.when(envelope.getDeliveryTag()).thenReturn(random.nextLong());
			GetResponse response = Mockito.mock(GetResponse.class);
			Mockito.when(response.getProps()).thenReturn(props);
			Mockito.when(response.getEnvelope()).thenReturn(envelope);
			Mockito.when(response.getBody()).thenReturn("{}".getBytes());
			Mockito.when(response.getMessageCount()).thenReturn(bound - i);
			queue.add(response);
		}
		return queue;
	}

	Channel mockBaseChannel() throws IOException, TimeoutException {
		Channel channel = Mockito.mock(Channel.class);
		Mockito.when(channel.isOpen()).thenReturn(Boolean.TRUE);
		Mockito.doAnswer(invocation -> {
			Mockito.when(channel.isOpen()).thenReturn(Boolean.FALSE);
			return DoesNothing.doesNothing();
		}).when(channel).close();
		return channel;
	}

	Channel mockChannelForQueue(Channel channel, boolean isWorking, boolean exists, String name,
			List<GetResponse> queue) throws IOException {
		// queueDeclarePassive
		final AMQImpl.Queue.DeclareOk queueDeclareOK = new AMQImpl.Queue.DeclareOk(name, queue.size(), 1);
		if (exists) {
			Mockito.when(channel.queueDeclarePassive(eq(name))).thenReturn(queueDeclareOK);
		} else {
			Mockito.when(channel.queueDeclarePassive(eq(name))).thenThrow(new IOException("Queue " + name + " exists"));
		}
		// queueDeclare
		OngoingStubbing<AMQP.Queue.DeclareOk> declareOkOngoingStubbing = Mockito.when(channel.queueDeclare(eq(name),
				Mockito.anyBoolean(), Mockito.anyBoolean(), Mockito.anyBoolean(), Mockito.anyMap()))
				.thenReturn(queueDeclareOK);
		if (!isWorking) {
			declareOkOngoingStubbing.thenThrow(new IOException("Cannot declare queue " + name),
					new RuntimeException("Not working"));
		}
		// messageCount
		Mockito.when(channel.messageCount(eq(name))).thenReturn(1l * queue.size());
		// basicGet
		OngoingStubbing<String> getResponseOngoingStubbing = Mockito
				.when(channel.basicConsume(eq(name), Mockito.anyBoolean(), (Consumer) Mockito.any(Consumer.class))).thenAnswer(new ReturnsElementsOf(queue));
		if (!isWorking) {
			getResponseOngoingStubbing.thenThrow(new IOException("Not working"), new RuntimeException("Not working"));
		}
		// basicPublish
		if (isWorking) {
			Mockito.doNothing().when(channel).basicPublish(eq(StringUtils.EMPTY), eq(name),
					Mockito.any(AMQP.BasicProperties.class), Mockito.any(byte[].class));
		} else {
			Mockito.doThrow(new IOException("Not working")).when(channel).basicPublish(eq(StringUtils.EMPTY), eq(name),
					Mockito.any(AMQP.BasicProperties.class), Mockito.any(byte[].class));
		}
		return channel;
	}

	Channel mockChannelForExchange(Channel channel, boolean isWorking, boolean exists, String queueName, String name,
			String type, String routingKey, List<GetResponse> queue) throws IOException {
		// exchangeDeclarePassive
		final AMQImpl.Exchange.DeclareOk exchangeDeclareOK = new AMQImpl.Exchange.DeclareOk();
		if (exists) {
			Mockito.when(channel.exchangeDeclarePassive(eq(name))).thenReturn(exchangeDeclareOK);
		} else {
			Mockito.when(channel.exchangeDeclarePassive(eq(name)))
					.thenThrow(new IOException("Exchange " + name + " exists"));
		}
		// exchangeDeclare
		OngoingStubbing<AMQP.Exchange.DeclareOk> declareOkOngoingStubbing = Mockito.when(channel
				.exchangeDeclare(eq(name), eq(type), Mockito.anyBoolean(), Mockito.anyBoolean(), Mockito.anyMap()))
				.thenReturn(exchangeDeclareOK);
		if (!isWorking) {
			declareOkOngoingStubbing.thenThrow(new IOException("Cannot declare exchange " + name + " of type " + type),
					new RuntimeException("Not working"));
		}
		// queueDeclarePassive
		final AMQImpl.Queue.DeclareOk queueDeclareOK = new AMQImpl.Queue.DeclareOk(queueName, queue.size(), 1);
		if (exists) {
			Mockito.when(channel.queueDeclarePassive(eq(queueName))).thenReturn(queueDeclareOK);
		} else {
			Mockito.when(channel.queueDeclarePassive(eq(queueName)))
					.thenThrow(new IOException("Queue " + queueName + " exists"));
		}
		// queueDeclare
		Mockito.when(channel.queueDeclare(eq(queueName), Mockito.anyBoolean(), Mockito.anyBoolean(),
				Mockito.anyBoolean(), Mockito.anyMap())).thenReturn(queueDeclareOK);
		// queueBind
		Mockito.when(channel.queueBind(eq(queueName), eq(name), eq(routingKey))).thenReturn(new AMQImpl.Queue.BindOk());
		// messageCount
		Mockito.when(channel.messageCount(eq(name))).thenReturn(1l * queue.size());
		// basicGet
		
		OngoingStubbing<String> getResponseOngoingStubbing = Mockito
				.when(channel.basicConsume(eq(queueName), Mockito.anyBoolean(), (Consumer) Mockito.any(Consumer.class))).thenAnswer(new ReturnsElementsOf(queue));
	
		if (!isWorking) {
			getResponseOngoingStubbing.thenThrow(new IOException("Not working"), new RuntimeException("Not working"));
		}
		// basicPublish
		if (isWorking) {
			Mockito.doNothing().when(channel).basicPublish(eq(name), eq(routingKey),
					Mockito.any(AMQP.BasicProperties.class), Mockito.any(byte[].class));
		} else {
			Mockito.doThrow(new IOException("Not working")).when(channel).basicPublish(eq(name), eq(routingKey),
					Mockito.any(AMQP.BasicProperties.class), Mockito.any(byte[].class));
		}
		return channel;
	}

	Connection mockGoodConnection(Channel channel) throws IOException {
		Connection connection = Mockito.mock(Connection.class);
		Mockito.when(connection.createChannel()).thenReturn(channel);
		Mockito.when(connection.isOpen()).thenReturn(Boolean.TRUE);
		Mockito.doAnswer(invocation -> {
			Mockito.when(connection.isOpen()).thenReturn(Boolean.FALSE);
			return DoesNothing.doesNothing();
		}).when(connection).close();
		return connection;
	}

	Connection mockBadConnection() throws IOException {
		Connection connection = Mockito.mock(Connection.class);
		Mockito.when(connection.createChannel()).thenThrow(new IOException("Can't create channel"));
		Mockito.when(connection.isOpen()).thenReturn(Boolean.TRUE);
		Mockito.doThrow(new IOException("Can't close connection")).when(connection).close();
		return connection;
	}

	ConnectionFactory mockConnectionFactory(Connection connection) throws IOException, TimeoutException {
		ConnectionFactory connectionFactory = Mockito.mock(ConnectionFactory.class);
		Mockito.when(connectionFactory.newConnection(eq(addresses))).thenReturn(connection);
		return connectionFactory;
	}

	void runObserve(Channel channel, AMQPObservableQueue observableQueue, String queueName, boolean useWorkingChannel,
			int batchSize) throws IOException {

		final List<Message> found = new ArrayList<>(batchSize);
		TestSubscriber<Message> subscriber = TestSubscriber.create(Subscribers.create(found::add));
		rx.Observable<Message> observable = observableQueue.observe().take(pollTimeMs * 2, TimeUnit.MILLISECONDS);
		assertNotNull(observable);
		observable.subscribe(subscriber);
		subscriber.awaitTerminalEvent();
		subscriber.assertNoErrors();
		subscriber.assertCompleted();
		if (useWorkingChannel) {
			verify(channel, atLeast(1)).basicConsume(eq(queueName), Mockito.anyBoolean(),(Consumer)Mockito.any(Consumer.class));
			Mockito.doNothing().when(channel).basicAck(Mockito.anyLong(), eq(false));
			Mockito.doAnswer(DoesNothing.doesNothing()).when(channel).basicAck(Mockito.anyLong(), eq(false));
			observableQueue.ack(Collections.synchronizedList(found));
		} else {
			assertNotNull(found);
			assertTrue(found.isEmpty());
		}
		observableQueue.close();
	}

	// Tests

	@Test
	public void testGetMessagesFromExistingExchangeAndDefaultConfiguration() throws IOException, TimeoutException {
		// Mock channel and connection
		Channel channel = mockBaseChannel();
		Connection connection = mockGoodConnection(channel);
		testGetMessagesFromExchangeAndDefaultConfiguration(channel, connection, true, true);
	}

	@Test
	public void testGetMessagesFromNotExistingExchangeAndDefaultConfiguration() throws IOException, TimeoutException {
		// Mock channel and connection
		Channel channel = mockBaseChannel();
		Connection connection = mockGoodConnection(channel);
		testGetMessagesFromExchangeAndDefaultConfiguration(channel, connection, false, true);
	}

	@Test
	public void testGetMessagesFromExistingExchangeWithDurableExclusiveAutoDeleteQueueConfiguration() throws IOException, TimeoutException {
		// Mock channel and connection
		Channel channel = mockBaseChannel();
		Connection connection = mockGoodConnection(channel);
		testGetMessagesFromExchangeAndCustomConfigurationFromURI(channel, connection, true, true, true, true, true);
	}

	@Test
	public void testGetMessagesFromNotExistingExchangeWithNonDurableNonExclusiveNonAutoDeleteQueueConfiguration() throws IOException, TimeoutException {
		// Mock channel and connection
		Channel channel = mockBaseChannel();
		Connection connection = mockGoodConnection(channel);
		testGetMessagesFromExchangeAndCustomConfigurationFromURI(channel, connection, false, true, false, false, false);
	}

	@Test
	public void testGetMessagesFromNotExistingExchangeWithDurableExclusiveNonAutoDeleteQueueConfiguration() throws IOException, TimeoutException {
		// Mock channel and connection
		Channel channel = mockBaseChannel();
		Connection connection = mockGoodConnection(channel);
		testGetMessagesFromExchangeAndCustomConfigurationFromURI(channel, connection, false, true, true, true, false);
	}

	@Test
	public void testPublishMessagesToNotExistingExchangeAndDefaultConfiguration() throws IOException, TimeoutException {
		// Mock channel and connection
		Channel channel = mockBaseChannel();
		Connection connection = mockGoodConnection(channel);
		testPublishMessagesToExchangeAndDefaultConfiguration(channel, connection, false, true);
	}

	@Test(expected = RuntimeException.class)
	public void testGetMessagesFromExchangeWithBadConnection() throws IOException, TimeoutException {
		// Mock channel and connection
		Channel channel = mockBaseChannel();
		Connection connection = mockBadConnection();
		testGetMessagesFromExchangeAndDefaultConfiguration(channel, connection, true, true);
	}

	@Test(expected = RuntimeException.class)
	public void testPublishMessagesToExchangeWithBadConnection() throws IOException, TimeoutException {
		// Mock channel and connection
		Channel channel = mockBaseChannel();
		Connection connection = mockBadConnection();
		testPublishMessagesToExchangeAndDefaultConfiguration(channel, connection, true, true);
	}

	@Test
	public void testGetMessagesFromExchangeWithBadChannel() throws IOException, TimeoutException {
		// Mock channel and connection
		Channel channel = mockBaseChannel();
		Connection connection = mockGoodConnection(channel);
		testGetMessagesFromExchangeAndDefaultConfiguration(channel, connection, true, false);
	}

	@Test(expected = RuntimeException.class)
	public void testPublishMessagesToExchangeWithBadChannel() throws IOException, TimeoutException {
		// Mock channel and connection
		Channel channel = mockBaseChannel();
		Connection connection = mockGoodConnection(channel);
		testPublishMessagesToExchangeAndDefaultConfiguration(channel, connection, true, false);
	}

	@Test
	public void testAck() throws IOException, TimeoutException {
		// Mock channel and connection
		Channel channel = mockBaseChannel();
		Connection connection = mockGoodConnection(channel);
		final Random random = new Random();

		final String name = RandomStringUtils.randomAlphabetic(30), type = "topic",
				routingKey = RandomStringUtils.randomAlphabetic(30);

		final AMQPSettings settings = new AMQPSettings(configuration).fromURI("amqp_exchange:" + name + "?exchangeType="
				+ type + "&routingKey=" + routingKey);
		AMQPObservableQueue observableQueue = new AMQPObservableQueue(mockConnectionFactory(connection), addresses,
				true, settings, batchSize, pollTimeMs);
		List<Message> messages = new LinkedList<>();
		Message msg = new Message();
		msg.setId("0e3eef8f-ebb1-4244-9665-759ab5bdf433");
		msg.setPayload("Payload");
		msg.setReceipt("1");
		messages.add(msg);
		List<String> deliveredTags = observableQueue.ack(messages);
		assertNotNull(deliveredTags);
	}

	private void testGetMessagesFromExchangeAndDefaultConfiguration(Channel channel, Connection connection,
			boolean exists, boolean useWorkingChannel) throws IOException, TimeoutException {

		final Random random = new Random();

		final String name = RandomStringUtils.randomAlphabetic(30), type = "topic",
				routingKey = RandomStringUtils.randomAlphabetic(30);
		final String queueName = String.format("bound_to_%s", name);

		final AMQPSettings settings = new AMQPSettings(configuration).fromURI("amqp_exchange:" + name + "?exchangeType="
				+ type + "&routingKey=" + routingKey);
		assertEquals(true, settings.isDurable());
		assertEquals(false, settings.isExclusive());
		assertEquals(false, settings.autoDelete());
		assertEquals(2, settings.getDeliveryMode());
		assertEquals(name, settings.getQueueOrExchangeName());
		assertEquals(type, settings.getExchangeType());
		assertEquals(routingKey, settings.getRoutingKey());

		List<GetResponse> queue = buildQueue(random, batchSize);
		channel = mockChannelForExchange(channel, useWorkingChannel, exists, queueName, name, type, routingKey, queue);

		AMQPObservableQueue observableQueue = new AMQPObservableQueue(mockConnectionFactory(connection), addresses,
				true, settings, batchSize, pollTimeMs);

		assertArrayEquals(addresses, observableQueue.getAddresses());
		assertEquals(AMQPConstants.AMQP_EXCHANGE_TYPE, observableQueue.getType());
		assertEquals(AMQPConstants.AMQP_EXCHANGE_TYPE+":"+name+"?exchangeType="
				+ type + "&routingKey=" + routingKey, observableQueue.getName());
		assertEquals(name, observableQueue.getURI());
		assertEquals(batchSize, observableQueue.getBatchSize());
		assertEquals(pollTimeMs, observableQueue.getPollTimeInMS());
		assertEquals(queue.size(), observableQueue.size());

		runObserve(channel, observableQueue, queueName, useWorkingChannel, batchSize);

		if (useWorkingChannel) {
			if (exists) {
				verify(channel, atLeastOnce()).exchangeDeclarePassive(eq(name));
			} else {
				verify(channel, atLeastOnce()).exchangeDeclare(eq(name), eq(type), eq(settings.isDurable()),
						eq(settings.autoDelete()), eq(Collections.emptyMap()));
				verify(channel, atLeastOnce()).queueDeclare(eq(queueName), eq(settings.isDurable()), eq(settings.isExclusive()), eq(settings.autoDelete()),
						anyMap());
			}
			verify(channel, atLeastOnce()).queueBind(eq(queueName), eq(name), eq(routingKey));
		}
	}

	private void testGetMessagesFromExchangeAndCustomConfigurationFromURI(Channel channel, Connection connection,
			boolean exists, boolean useWorkingChannel, boolean durable, boolean exclusive, boolean autoDelete) throws IOException, TimeoutException {

		final Random random = new Random();

		final String name = RandomStringUtils.randomAlphabetic(30), type = "topic",
				routingKey = RandomStringUtils.randomAlphabetic(30);
		final String queueName = String.format("bound_to_%s", name);

		final AMQPSettings settings = new AMQPSettings(configuration).fromURI("amqp_exchange:" + name + "?exchangeType="
				+ type + "&routingKey=" + routingKey + "&deliveryMode=2"
				+ "&durable=" + durable + "&exclusive=" + exclusive + "&autoDelete=" + autoDelete);
		assertEquals(durable, settings.isDurable());
		assertEquals(exclusive, settings.isExclusive());
		assertEquals(autoDelete, settings.autoDelete());
		assertEquals(2, settings.getDeliveryMode());
		assertEquals(name, settings.getQueueOrExchangeName());
		assertEquals(type, settings.getExchangeType());
		assertEquals(routingKey, settings.getRoutingKey());

		List<GetResponse> queue = buildQueue(random, batchSize);
		channel = mockChannelForExchange(channel, useWorkingChannel, exists, queueName, name, type, routingKey, queue);

		AMQPObservableQueue observableQueue = new AMQPObservableQueue(mockConnectionFactory(connection), addresses,
				true, settings, batchSize, pollTimeMs);

		assertArrayEquals(addresses, observableQueue.getAddresses());
		assertEquals(AMQPConstants.AMQP_EXCHANGE_TYPE, observableQueue.getType());
		assertEquals(AMQPConstants.AMQP_EXCHANGE_TYPE+":"+name+"?exchangeType="
						+ type + "&routingKey=" + routingKey + "&deliveryMode=2"
						+ "&durable="+ durable + "&exclusive=" + exclusive + "&autoDelete=" + autoDelete,
				observableQueue.getName());
		assertEquals(name, observableQueue.getURI());
		assertEquals(batchSize, observableQueue.getBatchSize());
		assertEquals(pollTimeMs, observableQueue.getPollTimeInMS());
		assertEquals(queue.size(), observableQueue.size());

		runObserve(channel, observableQueue, queueName, useWorkingChannel, batchSize);

		if (useWorkingChannel) {
			if (exists) {
				verify(channel, atLeastOnce()).exchangeDeclarePassive(eq(name));
			} else {
				verify(channel, atLeastOnce()).exchangeDeclare(eq(name), eq(type), eq(settings.isDurable()),
						eq(settings.autoDelete()), eq(Collections.emptyMap()));
				verify(channel, atLeastOnce()).queueDeclare(eq(queueName), eq(settings.isDurable()), eq(settings.isExclusive()), eq(settings.autoDelete()),
						anyMap());
			}
			verify(channel, atLeastOnce()).queueBind(eq(queueName), eq(name), eq(routingKey));
		}
	}

	private void testPublishMessagesToExchangeAndDefaultConfiguration(Channel channel, Connection connection,
			boolean exists, boolean useWorkingChannel) throws IOException, TimeoutException {
		final Random random = new Random();

		final String name = RandomStringUtils.randomAlphabetic(30), type = "topic",
				queueName = RandomStringUtils.randomAlphabetic(30), routingKey = RandomStringUtils.randomAlphabetic(30);

		final AMQPSettings settings = new AMQPSettings(configuration).fromURI("amqp_exchange:" + name + "?exchangeType="
				+ type + "&routingKey=" + routingKey + "&deliveryMode=2&durable=true&exclusive=false&autoDelete=true");
		assertEquals(true, settings.isDurable());
		assertEquals(false, settings.isExclusive());
		assertEquals(true, settings.autoDelete());
		assertEquals(2, settings.getDeliveryMode());
		assertEquals(name, settings.getQueueOrExchangeName());
		assertEquals(type, settings.getExchangeType());
		assertEquals(routingKey, settings.getRoutingKey());

		List<GetResponse> queue = buildQueue(random, batchSize);
		channel = mockChannelForExchange(channel, useWorkingChannel, exists, queueName, name, type, routingKey, queue);

		AMQPObservableQueue observableQueue = new AMQPObservableQueue(mockConnectionFactory(connection), addresses,
				true, settings, batchSize, pollTimeMs);

		assertArrayEquals(addresses, observableQueue.getAddresses());
		assertEquals(AMQPConstants.AMQP_EXCHANGE_TYPE, observableQueue.getType());
		assertEquals(AMQPConstants.AMQP_EXCHANGE_TYPE+":"+name+"?exchangeType="+ type + "&routingKey=" + routingKey + "&deliveryMode=2&durable=true&exclusive=false&autoDelete=true", observableQueue.getName());
		assertEquals(name, observableQueue.getURI());
		assertEquals(batchSize, observableQueue.getBatchSize());
		assertEquals(pollTimeMs, observableQueue.getPollTimeInMS());
		assertEquals(queue.size(), observableQueue.size());

		List<Message> messages = new LinkedList<>();
		Observable.range(0, batchSize).forEach((Integer x) -> messages.add(new Message("" + x, "payload: " + x, null)));
		assertEquals(batchSize, messages.size());
		observableQueue.publish(messages);

		if (useWorkingChannel) {
			verify(channel, times(batchSize)).basicPublish(eq(name), eq(routingKey), any(AMQP.BasicProperties.class),
					any(byte[].class));
		}
	}
	 @Test
	    public void testGetMessagesFromExistingQueueAndDefaultConfiguration()
	            throws IOException, TimeoutException {
	        // Mock channel and connection
	        Channel channel = mockBaseChannel();
	        Connection connection = mockGoodConnection(channel);
	        testGetMessagesFromQueueAndDefaultConfiguration(channel, connection,true, true);
	    }

	    @Test
	    public void testGetMessagesFromNotExistingQueueAndDefaultConfiguration()
	            throws IOException, TimeoutException {
	        // Mock channel and connection
	        Channel channel = mockBaseChannel();
	        Connection connection = mockGoodConnection(channel);
	        testGetMessagesFromQueueAndDefaultConfiguration(channel, connection,false, true);
	    }

	    @Test
	    public void testPublishMessagesToNotExistingQueueAndDefaultConfiguration()
	            throws IOException, TimeoutException {
	        // Mock channel and connection
	        Channel channel = mockBaseChannel();
	        Connection connection = mockGoodConnection(channel);
	        testPublishMessagesToQueueAndDefaultConfiguration(channel, connection,false, true);
	    }

	    @Test(expected = RuntimeException.class)
	    public void testGetMessagesFromQueueWithBadConnection()
	            throws IOException, TimeoutException {
	        // Mock channel and connection
	        Channel channel = mockBaseChannel();
	        Connection connection = mockBadConnection();
	        testGetMessagesFromQueueAndDefaultConfiguration(channel, connection, true, true);
	    }

	    @Test(expected = RuntimeException.class)
	    public void testPublishMessagesToQueueWithBadConnection()
	            throws IOException, TimeoutException {
	        // Mock channel and connection
	        Channel channel = mockBaseChannel();
	        Connection connection = mockBadConnection();
	        testPublishMessagesToQueueAndDefaultConfiguration(channel, connection, true, true);
	    }

	    @Test
	    public void testGetMessagesFromQueueWithBadChannel()
	            throws IOException, TimeoutException {
	        // Mock channel and connection
	        Channel channel = mockBaseChannel();
	        Connection connection = mockGoodConnection(channel);
	        testGetMessagesFromQueueAndDefaultConfiguration(channel, connection, true, false);
	    }

	    @Test(expected = RuntimeException.class)
	    public void testPublishMessagesToQueueWithBadChannel()
	            throws IOException, TimeoutException {
	        // Mock channel and connection
	        Channel channel = mockBaseChannel();
	        Connection connection = mockGoodConnection(channel);
	        testPublishMessagesToQueueAndDefaultConfiguration(channel, connection, true, false);
	    }
	    
	    @Test(expected = IllegalArgumentException.class)
	    public void testAMQPObservalbleQueue_empty()
	            throws IOException, TimeoutException {
	        AMQPSettings settings = new AMQPSettings(configuration).fromURI("amqp_queue:test");
	        AMQPObservableQueue observableQueue = new AMQPObservableQueue(null,
	                addresses, false, settings, batchSize, pollTimeMs);
	    }
	    @Test(expected = IllegalArgumentException.class)
	    public void testAMQPObservalbleQueue_addressEmpty()
	            throws IOException, TimeoutException {
	        AMQPSettings settings = new AMQPSettings(configuration).fromURI("amqp_queue:test");
	        AMQPObservableQueue observableQueue = new AMQPObservableQueue(mockConnectionFactory(mockGoodConnection(mockBaseChannel())),
	                null, false, settings, batchSize, pollTimeMs);
	    }
	    @Test(expected = IllegalArgumentException.class)
	    public void testAMQPObservalbleQueue_settingsEmpty()
	            throws IOException, TimeoutException {
	        AMQPSettings settings = new AMQPSettings(configuration).fromURI("amqp_queue:test");
	        AMQPObservableQueue observableQueue = new AMQPObservableQueue(mockConnectionFactory(mockGoodConnection(mockBaseChannel())),
	                addresses, false, null, batchSize, pollTimeMs);
	    }
	    
	    @Test(expected = IllegalArgumentException.class)
	    public void testAMQPObservalbleQueue_batchsizezero()
	            throws IOException, TimeoutException {
	        AMQPSettings settings = new AMQPSettings(configuration).fromURI("amqp_queue:test");
	        AMQPObservableQueue observableQueue = new AMQPObservableQueue(mockConnectionFactory(mockGoodConnection(mockBaseChannel())),
	                addresses, false, settings, 0, pollTimeMs);
	    }
	    @Test(expected = IllegalArgumentException.class)
	    public void testAMQPObservalbleQueue_polltimezero()
	            throws IOException, TimeoutException {
	        AMQPSettings settings = new AMQPSettings(configuration).fromURI("amqp_queue:test");
	        AMQPObservableQueue observableQueue = new AMQPObservableQueue(mockConnectionFactory(mockGoodConnection(mockBaseChannel())),
	                addresses, false, settings, batchSize, 0);
	    }
	    
	    @Test
	    public void testclosetExistingQueueAndDefaultConfiguration()
	            throws IOException, TimeoutException {
	        // Mock channel and connection
	        Channel channel = mockBaseChannel();
	        Connection connection = mockGoodConnection(channel);
	        testGetMessagesFromQueueAndDefaultConfiguration_close(channel, connection,false, true);
	    }
	    private void testGetMessagesFromQueueAndDefaultConfiguration(Channel channel, Connection connection,
	                                                                 boolean queueExists, boolean useWorkingChannel)
	            throws IOException, TimeoutException {
	        final Random random = new Random();

	        final String queueName = RandomStringUtils.randomAlphabetic(30);
	        AMQPSettings settings = new AMQPSettings(configuration).fromURI("amqp_queue:" + queueName);

	        List<GetResponse> queue = buildQueue(random, batchSize);
	        channel = mockChannelForQueue(channel, useWorkingChannel, queueExists, queueName, queue);

	        AMQPObservableQueue observableQueue = new AMQPObservableQueue(
	                mockConnectionFactory(connection),
	                addresses, false, settings, batchSize, pollTimeMs);

	        assertArrayEquals(addresses, observableQueue.getAddresses());
	        assertEquals(AMQPConstants.AMQP_QUEUE_TYPE, observableQueue.getType());
	        assertEquals(AMQPConstants.AMQP_QUEUE_TYPE+":"+queueName, observableQueue.getName());
	        assertEquals(queueName, observableQueue.getURI());
	        assertEquals(batchSize, observableQueue.getBatchSize());
	        assertEquals(pollTimeMs, observableQueue.getPollTimeInMS());
	        assertEquals(queue.size(), observableQueue.size());

	        runObserve(channel, observableQueue, queueName, useWorkingChannel, batchSize);
	    }
	    
	    private void testGetMessagesFromQueueAndDefaultConfiguration_close(Channel channel, Connection connection,
                boolean queueExists, boolean useWorkingChannel)
throws IOException, TimeoutException {
final Random random = new Random();

final String queueName = RandomStringUtils.randomAlphabetic(30);
AMQPSettings settings = new AMQPSettings(configuration).fromURI("amqp_queue:" + queueName);

List<GetResponse> queue = buildQueue(random, batchSize);
channel = mockChannelForQueue(channel, useWorkingChannel, queueExists, queueName, queue);

AMQPObservableQueue observableQueue = new AMQPObservableQueue(
mockConnectionFactory(connection),
addresses, false, settings, batchSize, pollTimeMs);
observableQueue.close();
assertArrayEquals(addresses, observableQueue.getAddresses());
assertEquals(AMQPConstants.AMQP_QUEUE_TYPE, observableQueue.getType());
assertEquals(AMQPConstants.AMQP_QUEUE_TYPE+":"+queueName, observableQueue.getName());
assertEquals(queueName, observableQueue.getURI());
assertEquals(batchSize, observableQueue.getBatchSize());
assertEquals(pollTimeMs, observableQueue.getPollTimeInMS());
assertEquals(queue.size(), observableQueue.size());

}

	    private void testPublishMessagesToQueueAndDefaultConfiguration(Channel channel, Connection connection,
	                                                                 boolean queueExists, boolean useWorkingChannel)
	            throws IOException, TimeoutException {
	        final Random random = new Random();

	        final String queueName = RandomStringUtils.randomAlphabetic(30);
	        final AMQPSettings settings = new AMQPSettings(configuration)
	                .fromURI("amqp_queue:" + queueName +"?deliveryMode=2&durable=true&exclusive=false&autoDelete=true");
	        assertEquals(true, settings.isDurable());
	        assertEquals(false, settings.isExclusive());
	        assertEquals(true, settings.autoDelete());
	        assertEquals(2, settings.getDeliveryMode());

	        List<GetResponse> queue = buildQueue(random, batchSize);
	        channel = mockChannelForQueue(channel, useWorkingChannel, queueExists, queueName, queue);

	        AMQPObservableQueue observableQueue = new AMQPObservableQueue(
	                mockConnectionFactory(connection),
	                addresses, false, settings, batchSize, pollTimeMs);

	        assertArrayEquals(addresses, observableQueue.getAddresses());
	        assertEquals(AMQPConstants.AMQP_QUEUE_TYPE, observableQueue.getType());
	        assertEquals(AMQPConstants.AMQP_QUEUE_TYPE+":"+queueName+"?deliveryMode=2&durable=true&exclusive=false&autoDelete=true", observableQueue.getName());
	        assertEquals(queueName, observableQueue.getURI());
	        assertEquals(batchSize, observableQueue.getBatchSize());
	        assertEquals(pollTimeMs, observableQueue.getPollTimeInMS());
	        assertEquals(queue.size(), observableQueue.size());

	        List<Message> messages = new LinkedList<>();
	        Observable.range(0, batchSize).forEach((Integer x) -> messages.add(new Message("" + x, "payload: " + x, null)));
	        assertEquals(batchSize, messages.size());
	        observableQueue.publish(messages);

	        if (useWorkingChannel) {
	            verify(channel, times(batchSize)).basicPublish(eq(StringUtils.EMPTY), eq(queueName),
	                    any(AMQP.BasicProperties.class), any(byte[].class));
	        }
	    }
}
