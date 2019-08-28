package com.netflix.conductor.contribs.kafka;

import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.config.SystemPropertiesConfiguration;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.LongSerializer;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Properties;

public class TestKafkaProducerManager {


	@Test
	public void testRequestTimeoutSetFromDefault() {

		KafkaProducerManager manager = new KafkaProducerManager(new SystemPropertiesConfiguration());
		KafkaPublishTask.Input input = getInput();
		Properties props = manager.getProducerProperties(input);
		Assert.assertEquals(props.getProperty(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG), "100");

	}

	@Test
	public void testRequestTimeoutSetFromInput() {

		KafkaProducerManager manager = new KafkaProducerManager(new SystemPropertiesConfiguration());
		KafkaPublishTask.Input input = getInput();
		input.setRequestTimeoutMs(200);
		Properties props = manager.getProducerProperties(input);
		Assert.assertEquals(props.getProperty(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG), "200");

	}

	@Test
	public void testRequestTimeoutSetFromConfig() {


		Configuration configuration =  getConfiguration();
		KafkaProducerManager manager = new KafkaProducerManager(configuration);
		KafkaPublishTask.Input input = getInput();
		Properties props = manager.getProducerProperties(input);
		Assert.assertEquals(props.getProperty(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG), "150");

	}

	@Test(expected = RuntimeException.class)
	public void testExecutionException() {

		Configuration configuration =  getConfiguration();
		KafkaProducerManager manager = new KafkaProducerManager(configuration);
		KafkaPublishTask.Input input = getInput();
		Producer producer = manager.getProducer(input);
		Assert.assertNotNull(producer);

	}

	@Test
	public void testCacheInvalidation() {
		Configuration configuration =  getConfiguration();
		KafkaProducerManager manager = new KafkaProducerManager(configuration);
		KafkaPublishTask.Input input = getInput();
		input.setBootStrapServers("");
		Properties props = manager.getProducerProperties(input);
		Producer producerMock = Mockito.mock(Producer.class);
		Producer producer = manager.getFromCache(props, () -> producerMock);
		Assert.assertNotNull(producer);
		Mockito.verify(producerMock, Mockito.times(1)).close();
	}

	@Test
	public void testMaxBlockMsFromConfig() {

		Configuration configuration = getConfiguration();
		KafkaProducerManager manager = new KafkaProducerManager(configuration);
		KafkaPublishTask.Input input = getInput();
		Properties props = manager.getProducerProperties(input);
		Assert.assertEquals(props.getProperty(ProducerConfig.MAX_BLOCK_MS_CONFIG), "500");

	}



	@Test
	public void testMaxBlockMsFromInput() {

		Configuration configuration = getConfiguration();
		KafkaProducerManager manager = new KafkaProducerManager(configuration);
		KafkaPublishTask.Input input = getInput();
		input.setMaxBlockMs(600);
		Properties props = manager.getProducerProperties(input);
		Assert.assertEquals(props.getProperty(ProducerConfig.MAX_BLOCK_MS_CONFIG), "600");

	}

	private Configuration getConfiguration() {
		Configuration configuration = Mockito.mock(Configuration.class);
		Mockito.when(configuration.getProperty(Mockito.eq("kafka.publish.request.timeout.ms"), Mockito.eq("100"))).thenReturn("150");
		Mockito.when(configuration.getProperty(Mockito.eq("kafka.publish.max.block.ms"), Mockito.eq("500"))).thenReturn("500");
		return configuration;
	}

	private KafkaPublishTask.Input getInput() {
		KafkaPublishTask.Input input = new KafkaPublishTask.Input();
		input.setTopic("testTopic");
		input.setValue("TestMessage");
		input.setKeySerializer(LongSerializer.class.getCanonicalName());
		input.setBootStrapServers("servers");
		return input;
	}

}
