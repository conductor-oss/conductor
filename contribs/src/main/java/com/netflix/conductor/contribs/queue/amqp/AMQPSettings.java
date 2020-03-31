/**
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.contribs.queue.amqp;

import com.netflix.conductor.core.config.Configuration;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.netflix.conductor.contribs.queue.amqp.AMQPConfigurations.*;

/**
 * @author Ritu Parathody
 * 
 */
public class AMQPSettings {

	private static final Pattern URI_PATTERN = Pattern
			.compile("^(?:amqp\\_(queue|exchange))?\\:?(?<name>[^\\?]+)\\??(?<params>.*)$", Pattern.CASE_INSENSITIVE);

	private String queueOrExchangeName;
	private String eventName;
	private String exchangeType;
	private String routingKey;
	private String contentEncoding;
	private String contentType;

	private boolean durable;
	private boolean exclusive;
	private boolean autoDelete;

	private int deliveryMode;

	private final Map<String, Object> arguments = new HashMap<>();
	private static Logger logger = LoggerFactory.getLogger(AMQPSettings.class);

	public AMQPSettings(final Configuration config) {
		// Initialize with a default values
		durable = config.getBooleanProperty(String.format(AMQPConstants.PROPERTY_KEY_TEMPLATE, PROPERTY_IS_DURABLE),
				AMQPConstants.DEFAULT_DURABLE);
		exclusive = config.getBooleanProperty(String.format(AMQPConstants.PROPERTY_KEY_TEMPLATE, PROPERTY_IS_EXCLUSIVE),
				AMQPConstants.DEFAULT_EXCLUSIVE);
		autoDelete = config.getBooleanProperty(String.format(AMQPConstants.PROPERTY_KEY_TEMPLATE, PROPERTY_AUTO_DELETE),
				AMQPConstants.DEFAULT_AUTO_DELETE);
		contentType = config.getProperty(String.format(AMQPConstants.PROPERTY_KEY_TEMPLATE, PROPERTY_CONTENT_TYPE),
				AMQPConstants.DEFAULT_CONTENT_TYPE);
		contentEncoding = config.getProperty(
				String.format(AMQPConstants.PROPERTY_KEY_TEMPLATE, PROPERTY_CONTENT_ENCODING),
				AMQPConstants.DEFAULT_CONTENT_ENCODING);
		exchangeType = config.getProperty(
				String.format(AMQPConstants.PROPERTY_KEY_TEMPLATE, AMQPConstants.AMQP_EXCHANGE_TYPE),
				AMQPConstants.DEFAULT_EXCHANGE_TYPE);
		routingKey = StringUtils.EMPTY;
		// Set common settings for publishing and consuming
		setDeliveryMode(
				config.getIntProperty(String.format(AMQPConstants.PROPERTY_KEY_TEMPLATE, PROPERTY_DELIVERY_MODE),
						AMQPConstants.DEFAULT_DELIVERY_MODE));
	}

	public final boolean isDurable() {
		return durable;
	}

	public final boolean isExclusive() {
		return exclusive;
	}

	public final boolean autoDelete() {
		return autoDelete;
	}

	public final Map<String, Object> getArguments() {
		return arguments;
	}

	public final String getContentEncoding() {
		return contentEncoding;
	}

	/**
	 * Use queue for publishing
	 * 
	 * @param queueName the name of queue
	 */
	public void setQueue(String queueName) {
		if (StringUtils.isEmpty(queueName)) {
			throw new IllegalArgumentException("Queue name for publishing is undefined");
		}
		this.queueOrExchangeName = queueName;
	}

	public String getQueueOrExchangeName() {
		return queueOrExchangeName;
	}

	public String getExchangeType() {
		return exchangeType;
	}

	public String getRoutingKey() {
		return routingKey;
	}

	public int getDeliveryMode() {
		return deliveryMode;
	}

	public AMQPSettings setDeliveryMode(int deliveryMode) {
		if (deliveryMode != 1 && deliveryMode != 2) {
			throw new IllegalArgumentException("Delivery mode must be 1 or 2");
		}
		this.deliveryMode = deliveryMode;
		return this;
	}

	public String getContentType() {
		return contentType;
	}

	/**
	 * Complete settings from the queue URI.
	 *
	 * <u>Example for queue:</u>
	 * 
	 * <pre>
	 * amqp-queue:myQueue?deliveryMode=1&autoDelete=true&exclusive=true
	 * </pre>
	 * 
	 * <u>Example for exchange:</u>
	 * 
	 * <pre>
	 * amqp-exchange:myExchange?exchangeType=topic&routingKey=myRoutingKey&exclusive=true
	 * </pre>
	 * 
	 * @param queueURI
	 * @return
	 */
	public final AMQPSettings fromURI(final String queueURI) {
		final Matcher matcher = URI_PATTERN.matcher(queueURI);
		if (!matcher.matches()) {
			throw new IllegalArgumentException("Queue URI doesn't matches the expected regexp");
		}

		// Set name of queue or exchange from group "name"
		logger.info("Queue URI:{}", queueURI);
		queueOrExchangeName = matcher.group("name");
		eventName = queueURI;
		if (matcher.groupCount() > 1) {
			final String queryParams = matcher.group("params");
			if (StringUtils.isNotEmpty(queryParams)) {
				// Handle parameters
				Arrays.stream(queryParams.split("\\s*\\&\\s*")).forEach(param -> {
					final String[] kv = param.split("\\s*=\\s*");
					if (kv.length == 2) {
						if (kv[0].equalsIgnoreCase(String.valueOf(PARAM_EXCHANGE_TYPE))) {
							String value = kv[1];
							if (StringUtils.isEmpty(value)) {
								throw new IllegalArgumentException("The provided exchange type is empty");
							}
							exchangeType = value;
						}
						if (kv[0].equalsIgnoreCase((String.valueOf(PARAM_ROUTING_KEY)))) {
							String value = kv[1];
							if (StringUtils.isEmpty(value)) {
								throw new IllegalArgumentException("The provided routing key is empty");
							}
							routingKey = value;
						}
						if (kv[0].equalsIgnoreCase((String.valueOf(PARAM_DURABLE)))) {
							durable = Boolean.valueOf(kv[1]);
						}
						if (kv[0].equalsIgnoreCase((String.valueOf(PARAM_EXCLUSIVE)))) {
							exclusive = Boolean.valueOf(kv[1]);
						}
						if (kv[0].equalsIgnoreCase((String.valueOf(PARAM_AUTO_DELETE)))) {
							autoDelete = Boolean.valueOf(kv[1]);
						}
						if (kv[0].equalsIgnoreCase((String.valueOf(PARAM_DELIVERY_MODE)))) {
							setDeliveryMode(Integer.valueOf(kv[1]));
						}
						if (kv[0].equalsIgnoreCase((String.valueOf(PARAM_MAX_PRIORITY)))) {
							arguments.put("x-max-priority", Integer.valueOf(kv[1]));
						}
					}
				});
			}
		}
		return this;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof AMQPSettings))
			return false;
		AMQPSettings that = (AMQPSettings) o;
		return isDurable() == that.isDurable() && isExclusive() == that.isExclusive() && autoDelete == that.autoDelete
				&& getDeliveryMode() == that.getDeliveryMode()
				&& Objects.equals(getQueueOrExchangeName(), that.getQueueOrExchangeName())
				&& Objects.equals(getExchangeType(), that.getExchangeType())
				&& Objects.equals(getRoutingKey(), that.getRoutingKey())
				&& Objects.equals(getContentType(), that.getContentType())
				&& Objects.equals(getContentEncoding(), that.getContentEncoding())
				&& Objects.equals(getArguments(), that.getArguments());
	}

	@Override
	public int hashCode() {
		return Objects.hash(getQueueOrExchangeName(), getExchangeType(), getRoutingKey(), getContentType(), isDurable(),
				isExclusive(), autoDelete, getDeliveryMode(), getContentEncoding(), getArguments());
	}

	@Override
	public String toString() {
		return "AMQSettings{" + "queueOrExchangeName='" + queueOrExchangeName + '\'' + ", exchangeType='" + exchangeType
				+ '\'' + ", routingKey='" + routingKey + '\'' + ", contentType='" + contentType + '\'' + ", durable="
				+ durable + ", exclusive=" + exclusive + ", autoDelete=" + autoDelete + ", deliveryMode=" + deliveryMode
				+ ", contentEncoding='" + contentEncoding + '\'' + ", arguments=" + arguments + ", durable="
				+ isDurable() + ", exclusive=" + isExclusive() + '}';
	}

	public String getEventName() {
		return eventName;
	}
}