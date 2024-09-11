/*
 * Copyright 2022 Conductor Authors.
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
package io.orkes.conductor.client.http;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.client.exception.ConductorClientException;

import io.orkes.conductor.client.SecretClient;
import io.orkes.conductor.client.model.TagObject;
import io.orkes.conductor.client.util.ClientTestUtil;


public class SecretClientTests {
    private final String SECRET_NAME = "test-sdk-java-secret_name";
    private final String SECRET_KEY = "test-sdk-java-secret_key";

    private final SecretClient secretClient = ClientTestUtil.getOrkesClients().getSecretClient();

    @Test
    void testMethods() {
        try {
            secretClient.deleteSecret(SECRET_KEY);
        } catch (ConductorClientException e) {
            if (e.getStatus() != 500) {
                throw e;
            }
        }
        secretClient.putSecret(SECRET_NAME, SECRET_KEY);
        secretClient.setSecretTags(List.of(getTagObject()), SECRET_KEY);
        List<TagObject> tags = secretClient.getSecretTags(SECRET_KEY);
        Assertions.assertEquals(tags.size(), 1);
        Assertions.assertEquals(tags.get(0), getTagObject());
        secretClient.deleteSecretTags(List.of(getTagObject()), SECRET_KEY);
        Assertions.assertEquals(secretClient.getSecretTags(SECRET_KEY).size(), 0);
        Assertions.assertTrue(secretClient.listSecretsThatUserCanGrantAccessTo().contains(SECRET_KEY));
        Assertions.assertTrue(secretClient.listAllSecretNames().contains(SECRET_KEY));
        Assertions.assertEquals(SECRET_NAME, secretClient.getSecret(SECRET_KEY));
        Assertions.assertTrue(secretClient.secretExists(SECRET_KEY));
        secretClient.deleteSecret(SECRET_KEY);
    }

    private TagObject getTagObject() {
        TagObject tagObject = new TagObject();
        tagObject.setKey("department");
        tagObject.setValue("accounts");
        return tagObject;
    }
}
