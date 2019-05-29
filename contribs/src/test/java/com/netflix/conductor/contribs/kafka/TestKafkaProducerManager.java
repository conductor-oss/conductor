package com.netflix.conductor.contribs.kafka;

import com.netflix.conductor.core.config.SystemPropertiesConfiguration;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.LongSerializer;
import org.junit.Assert;
import org.junit.Test;

import java.util.Properties;

public class TestKafkaProducerManager {


	@Test
	public void testRequestTimeoutSet() {

		KafkaProducerManager manager = new KafkaProducerManager(new SystemPropertiesConfiguration());
		KafkaPublishTask.Input input = new KafkaPublishTask.Input();
		input.setTopic("testTopic");
		input.setValue("TestMessage");
		input.setKeySerializer(LongSerializer.class.getCanonicalName());
		input.setBootStrapServers("servers");
		Properties props = manager.getProducerProperties(input);
		Assert.assertEquals(props.getProperty(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG), "100");

	}
}
