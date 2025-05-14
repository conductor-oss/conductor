/*
 * Copyright 2025 Conductor Authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.orkes.conductor.client.model.integration;

import lombok.Getter;

import static io.orkes.conductor.client.model.integration.IntegrationDefFormField.IntegrationDefFormFieldType.*;

@Getter
public enum ConfigKey {

    api_key(PASSWORD),
    user(TEXT),
    endpoint(TEXT),
    authUrl(TEXT),
    environment(TEXT),
    projectName(TEXT),
    indexName(TEXT),
    publisher(TEXT),
    password(PASSWORD),
    namespace(TEXT),
    batchSize(TEXT),
    batchWaitTime(TEXT),
    visibilityTimeout(TEXT),
    connectionType(TEXT),
    connectionPoolSize(TEXT),
    consumer(TEXT),
    stream(TEXT),
    batchPollConsumersCount(TEXT),
    consumer_type(TEXT),
    region(TEXT),
    awsAccountId(PASSWORD),
    externalId(PASSWORD),
    roleArn(TEXT),
    protocol(TEXT),
    mechanism(TEXT),
    port(TEXT),
    schemaRegistryUrl(TEXT),
    schemaRegistryApiKey(TEXT),
    schemaRegistryApiSecret(PASSWORD),
    authenticationType(TEXT),
    truststoreAuthenticationType(TEXT),
    tls(TEXT),
    cipherSuite(TEXT),
    pubSubMethod(TEXT),
    keyStorePassword(PASSWORD),
    keyStoreLocation(TEXT),
    schemaRegistryAuthType(TEXT),
    valueSubjectNameStrategy(TEXT),

    datasourceURL(TEXT),
    jdbcDriver(TEXT),
    subscription(TEXT),
    serviceAccountCredentials(TEXT),
    file(FILE), //Need a new type TextArea to render an element for feeding Json
    tlsFile(FILE),
    queueManager(TEXT),
    groupId(TEXT),
    channel(TEXT),
    dimensions(TEXT),
    distance_metric(TEXT),
    indexing_method(TEXT),
    inverted_list_count(TEXT);


    private final IntegrationDefFormField.IntegrationDefFormFieldType type;

    ConfigKey(IntegrationDefFormField.IntegrationDefFormFieldType type) {
        this.type = type;
    }

}