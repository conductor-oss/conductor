/*
 * Copyright 2024 Orkes, Inc.
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
import java.util.Map;

import com.netflix.conductor.client.exception.ConductorClientException;
import com.netflix.conductor.client.http.ConductorClient;

import io.orkes.conductor.client.IntegrationClient;
import io.orkes.conductor.client.model.TagObject;
import io.orkes.conductor.client.model.integration.Integration;
import io.orkes.conductor.client.model.integration.IntegrationApi;
import io.orkes.conductor.client.model.integration.IntegrationApiUpdate;
import io.orkes.conductor.client.model.integration.IntegrationUpdate;
import io.orkes.conductor.client.model.integration.ai.PromptTemplate;

public class OrkesIntegrationClient implements IntegrationClient {
    private final IntegrationResource integrationResource;

    public OrkesIntegrationClient(ConductorClient apiClient) {
        this.integrationResource = new IntegrationResource(apiClient);
    }

    public void associatePromptWithIntegration(String integrationProvider, String integrationName, String promptName) {
        integrationResource.associatePromptWithIntegration(integrationProvider, integrationName, promptName);
    }

    public void deleteIntegrationApi(String integrationProvider, String integrationName) {
        integrationResource.deleteIntegrationApi(integrationProvider, integrationName);
    }

    public void deleteIntegrationProvider(String integrationName) {
        integrationResource.deleteIntegrationProvider(integrationName);
    }

    public IntegrationApi getIntegrationApi(String integrationProvider, String integrationName) {
        try {
            return integrationResource.getIntegrationApi(integrationProvider, integrationName);
        } catch (ConductorClientException e) {
            //FIXME WHY? this creates inconsistency. Should all 404 be a null?
            if (e.getStatus() == 404) {
                return null;
            }

            throw e;
        }
    }

    public List<IntegrationApi> getIntegrationApis(String integrationName) {
        return integrationResource.getIntegrationApis(integrationName, true);
    }

    public Integration getIntegrationProvider(String integrationProvider) {
        try {
            return integrationResource.getIntegrationProvider(integrationProvider);
        } catch (ConductorClientException e) {
            if (e.getStatus() == 404) {
                return null;
            }
            throw e;
        }
    }

    public List<Integration> getIntegrationProviders(String category, Boolean activeOnly) {
        return integrationResource.getIntegrationProviders(category, activeOnly);
    }

    public List<PromptTemplate> getPromptsWithIntegration(String aiIntegration, String modelName) {
        return integrationResource.getPromptsWithIntegration(aiIntegration, modelName);
    }

    public void saveIntegrationApi(String integrationName, String apiName, IntegrationApiUpdate integrationApiUpdate) {
        integrationResource.saveIntegrationApi(integrationApiUpdate, integrationName, apiName);
    }

    public void saveIntegration(String integrationName, IntegrationUpdate integrationDetails) {
        integrationResource.saveIntegrationProvider(integrationDetails, integrationName);
    }

    public int getTokenUsageForIntegration(String integrationProvider, String integrationName) {
        return integrationResource.getTokenUsageForIntegration(integrationProvider, integrationName);
    }

    public Map<String, Integer> getTokenUsageForIntegrationProvider(String integrationProvider) {
        return integrationResource.getTokenUsageForIntegrationProvider(integrationProvider);
    }

    // Tags - Implementations are assumed to be placeholders

    public void deleteTagForIntegrationProvider(List<TagObject> tags, String name) {
        integrationResource.deleteTagForIntegrationProvider(tags, name);
    }

    public void saveTagForIntegrationProvider(List<TagObject> tags, String name) {
        integrationResource.putTagForIntegrationProvider(tags, name);
    }

    public List<TagObject> getTagsForIntegrationProvider(String name) {
        return integrationResource.getTagsForIntegrationProvider(name);
    }
}
