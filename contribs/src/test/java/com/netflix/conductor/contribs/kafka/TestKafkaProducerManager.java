package com.netflix.conductor.contribs.kafka;

import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.config.SystemPropertiesConfiguration;
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
		KafkaPublishTask.Input input = new KafkaPublishTask.Input();
		input.setTopic("testTopic");
		input.setValue("TestMessage");
		input.setKeySerializer(LongSerializer.class.getCanonicalName());
		input.setBootStrapServers("servers");
		Properties props = manager.getProducerProperties(input);
		Assert.assertEquals(props.getProperty(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG), "100");

	}

	@Test
	public void testRequestTimeoutSetFromInput() {

		KafkaProducerManager manager = new KafkaProducerManager(new SystemPropertiesConfiguration());
		KafkaPublishTask.Input input = new KafkaPublishTask.Input();
		input.setTopic("testTopic");
		input.setValue("TestMessage");
		input.setKeySerializer(LongSerializer.class.getCanonicalName());
		input.setBootStrapServers("servers");
		input.setRequestTimeoutMs(200);
		Properties props = manager.getProducerProperties(input);
		Assert.assertEquals(props.getProperty(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG), "200");

	}

	@Test
	public void testRequestTimeoutSetFromConfig() {


		Configuration configuration =  Mockito.mock(Configuration.class);
		Mockito.when(configuration.getProperty(Mockito.eq("kafka.publish.request.timeout.ms"),Mockito.eq("100"))).thenReturn("150");
		KafkaProducerManager manager = new KafkaProducerManager(configuration);
		KafkaPublishTask.Input input = new KafkaPublishTask.Input();
		input.setTopic("testTopic");
		input.setValue("TestMessage");
		input.setKeySerializer(LongSerializer.class.getCanonicalName());
		input.setBootStrapServers("servers");
		Properties props = manager.getProducerProperties(input);
		Assert.assertEquals(props.getProperty(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG), "150");

	}
}
