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
package org.conductoross.conductor.ai.agentspan.runtime.ai;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.conductoross.conductor.ai.AIModel;
import org.conductoross.conductor.ai.AIModelProvider;
import org.conductoross.conductor.ai.ModelConfiguration;
import org.conductoross.conductor.ai.agentspan.runtime.credentials.CredentialResolutionService;
import org.conductoross.conductor.ai.agentspan.runtime.util.ProviderValidator;
import org.conductoross.conductor.ai.model.LLMWorkerInput;
import org.conductoross.conductor.ai.providers.anthropic.AnthropicConfiguration;
import org.conductoross.conductor.ai.providers.azureopenai.AzureOpenAIConfiguration;
import org.conductoross.conductor.ai.providers.cohere.CohereAIConfiguration;
import org.conductoross.conductor.ai.providers.gemini.GeminiVertexConfiguration;
import org.conductoross.conductor.ai.providers.grok.GrokAIConfiguration;
import org.conductoross.conductor.ai.providers.huggingface.HuggingFaceConfiguration;
import org.conductoross.conductor.ai.providers.mistral.MistralAIConfiguration;
import org.conductoross.conductor.ai.providers.ollama.OllamaConfiguration;
import org.conductoross.conductor.ai.providers.openai.OpenAIConfiguration;
import org.conductoross.conductor.ai.providers.perplexity.PerplexityAIConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.netflix.conductor.sdk.workflow.executor.task.TaskContext;

import okhttp3.OkHttpClient;

/**
 * Per-user LLM model provider that creates fresh AIModel instances with API keys from the
 * credential store.
 *
 * <p>Overrides {@link AIModelProvider#getModel(LLMWorkerInput)} to resolve per-user credentials via
 * {@link CredentialResolutionService}. If the user has a credential stored (e.g., {@code
 * OPENAI_API_KEY}), a fresh AIModel is created with that key. Otherwise falls back to the
 * server-wide model configured in application.properties.
 *
 * <p>Follows the same pattern as Orkes Conductor's {@code OrkesAIModelProvider}.
 */
@Component
@Primary
public class AgentspanAIModelProvider extends AIModelProvider {

    private static final Logger log = LoggerFactory.getLogger(AgentspanAIModelProvider.class);
    private final OkHttpClient conductorAiHttpClient;

    /** Maps Conductor provider names to credential env var names. */
    private static final Map<String, String> PROVIDER_TO_ENV_VAR =
            Map.ofEntries(
                    Map.entry("openai", "OPENAI_API_KEY"),
                    Map.entry("anthropic", "ANTHROPIC_API_KEY"),
                    Map.entry("mistral", "MISTRAL_API_KEY"),
                    Map.entry("cohere", "COHERE_API_KEY"),
                    Map.entry("grok", "XAI_API_KEY"),
                    Map.entry("perplexity", "PERPLEXITY_API_KEY"),
                    Map.entry("huggingface", "HUGGINGFACE_API_KEY"),
                    Map.entry("azureopenai", "AZURE_OPENAI_API_KEY"),
                    Map.entry("gemini", "GEMINI_API_KEY"),
                    Map.entry("google_gemini", "GEMINI_API_KEY"));

    /** Maps Conductor provider names to base URL env var names. */
    private static final Map<String, String> PROVIDER_TO_BASE_URL_ENV =
            Map.ofEntries(
                    Map.entry("openai", "OPENAI_BASE_URL"),
                    Map.entry("anthropic", "ANTHROPIC_BASE_URL"),
                    Map.entry("mistral", "MISTRAL_BASE_URL"),
                    Map.entry("cohere", "COHERE_BASE_URL"),
                    Map.entry("grok", "GROK_BASE_URL"),
                    Map.entry("perplexity", "PERPLEXITY_BASE_URL"),
                    Map.entry("azureopenai", "AZURE_OPENAI_BASE_URL"),
                    Map.entry("ollama", "OLLAMA_BASE_URL"));

    /** Providers that need no API key; a base URL alone is enough to build a model. */
    private static final Set<String> KEYLESS_PROVIDERS = Set.of("ollama");

    private final CredentialResolutionService resolutionService;

    /**
     * Spring constructor. The native credential resolution service is <em>optional</em>: when
     * {@code agentspan.embedded=true} it is gated off (the host delivers secrets), so it resolves
     * to {@code null} here and native resolution is skipped.
     */
    @Autowired
    public AgentspanAIModelProvider(
            List<ModelConfiguration<? extends AIModel>> modelConfigurations,
            Environment env,
            OkHttpClient conductorAiHttpClient,
            ObjectProvider<CredentialResolutionService> resolutionService) {
        this(modelConfigurations, env, conductorAiHttpClient, resolutionService.getIfAvailable());
    }

    /** Direct constructor (used by tests, and by the Spring constructor above). */
    public AgentspanAIModelProvider(
            List<ModelConfiguration<? extends AIModel>> modelConfigurations,
            Environment env,
            OkHttpClient conductorAiHttpClient,
            CredentialResolutionService resolutionService) {
        super(modelConfigurations, env);
        this.conductorAiHttpClient = conductorAiHttpClient;
        this.resolutionService = resolutionService;
        log.info(
                "AgentspanAIModelProvider initialized (native per-user credential resolution {})",
                resolutionService != null ? "enabled" : "disabled — embedded/host-delivered");
    }

    @Override
    public AIModel getModel(LLMWorkerInput input) {
        String provider = input.getLlmProvider();
        if (provider == null) {
            return super.getModel(input);
        }

        // Resolve per-agent base URL (from task input) or env var fallback
        String baseUrl = resolveBaseUrl(provider);

        // Try per-user credential resolution
        log.debug("getModel called for provider='{}' model='{}'", provider, input.getModel());
        String userApiKey = resolveUserApiKey(provider);
        log.debug(
                "resolveUserApiKey('{}') returned: {}",
                provider,
                userApiKey != null ? "key found" : "null");
        boolean keyless = KEYLESS_PROVIDERS.contains(provider.toLowerCase());
        if (userApiKey != null || baseUrl != null) {
            try {
                // If we have a base URL but no user key, try the server-wide key
                if (userApiKey == null && !keyless) {
                    String envVar = PROVIDER_TO_ENV_VAR.get(provider.toLowerCase());
                    userApiKey = envVar != null ? System.getenv(envVar) : null;
                }
                if (userApiKey != null || keyless) {
                    AIModel model = createModelWithKey(provider, userApiKey, baseUrl);
                    if (model != null) {
                        log.debug(
                                "Per-user AIModel created for provider '{}' baseUrl='{}'",
                                provider,
                                baseUrl);
                        getProviderToLLM().put(provider.toLowerCase(), model);
                        return model;
                    }
                }
            } catch (Exception e) {
                log.warn(
                        "Failed to create per-user AIModel for '{}': {}",
                        provider,
                        e.getMessage(),
                        e);
            }
        }

        // Fall back to server-wide model
        return super.getModel(input);
    }

    /**
     * Resolve the stored API key for the given LLM provider from the (global) credential store.
     *
     * @return the API key, or null if not found
     */
    private String resolveUserApiKey(String provider) {
        if (resolutionService == null) return null; // native resolution gated off (embedded)
        String envVarName = PROVIDER_TO_ENV_VAR.get(provider.toLowerCase());
        if (envVarName == null) return null;
        try {
            return resolutionService.resolve(envVarName);
        } catch (Exception e) {
            log.debug("Credential not found for provider '{}': {}", provider, e.getMessage());
            return null;
        }
    }

    /**
     * Returns true if the provider is available: either configured at startup (via environment
     * variables / application.properties) or has an API key credential in the current user's store.
     *
     * <p>Used by {@link ProviderValidator} so that credentials added via the UI take effect
     * immediately without requiring a server restart.
     */
    public boolean isProviderConfigured(String provider) {
        if (getProviderToLLM().containsKey(provider.toLowerCase())) return true;
        return resolveUserApiKey(provider) != null;
    }

    /**
     * Resolve the credential-store base URL for a provider (e.g. {@code OLLAMA_BASE_URL} for
     * ollama), or null when none is stored. Used by the provider-status endpoint — the server-side
     * source of truth that doctor and the UI query.
     */
    public String resolveConfiguredBaseUrl(String provider) {
        String envVarName = PROVIDER_TO_BASE_URL_ENV.get(provider.toLowerCase());
        if (envVarName == null) return null;
        String value = resolveUserCredential(envVarName);
        return (value != null && !value.isBlank()) ? value : null;
    }

    /** Resolve any named credential from the (global) credential store. */
    private String resolveUserCredential(String credentialName) {
        if (resolutionService == null) return null; // native resolution gated off (embedded)
        try {
            return resolutionService.resolve(credentialName);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Resolve base URL: per-agent (from task input) &gt; credential store &gt; null.
     *
     * <p>The credential store is the single source of truth. Env-var-set base URLs are seeded into
     * the store at startup by the host's credential env-seeder, so {@code System.getenv()} is never
     * read directly here.
     */
    @SuppressWarnings("unchecked")
    private String resolveBaseUrl(String provider) {
        // 1. Per-agent base URL from task input
        try {
            TaskContext ctx = TaskContext.get();
            if (ctx != null && ctx.getTask() != null) {
                Object taskBaseUrl = ctx.getTask().getInputData().get("baseUrl");
                if (taskBaseUrl instanceof String s && !s.isBlank()) {
                    log.debug("Using per-agent baseUrl for provider '{}': {}", provider, s);
                    return s;
                }
            }
        } catch (Exception e) {
            // ignore
        }

        String envVarName = PROVIDER_TO_BASE_URL_ENV.get(provider.toLowerCase());
        if (envVarName == null) return null;

        // 2. Credential store — covers both env-var-seeded credentials (populated at startup
        //    by the host's env-seeder) and credentials added manually via the UI.
        //    Direct System.getenv() is intentionally not used here: env vars are always
        //    seeded into the credential store at startup, so the store is the single
        //    source of truth and avoids bypassing external credential stores.
        String credVal = resolveUserCredential(envVarName);
        if (credVal != null && !credVal.isBlank()) {
            log.debug("Using credential {} for provider '{}': {}", envVarName, provider, credVal);
            return credVal;
        }

        return null;
    }

    /** Create a fresh AIModel instance with a per-user API key and optional base URL. */
    private AIModel createModelWithKey(String provider, String apiKey, String baseUrl) {
        ModelConfiguration<? extends AIModel> config =
                switch (provider.toLowerCase()) {
                    case "openai" ->
                            new OpenAIConfiguration(apiKey, baseUrl, null, conductorAiHttpClient);
                    case "anthropic" ->
                            new AnthropicConfiguration(
                                    apiKey, baseUrl, null, null, null, conductorAiHttpClient);
                    case "azureopenai" ->
                            new AzureOpenAIConfiguration(
                                    apiKey, baseUrl, null, null, conductorAiHttpClient);
                    case "mistral" ->
                            new MistralAIConfiguration(apiKey, baseUrl, conductorAiHttpClient);
                    case "ollama" ->
                            new OllamaConfiguration(baseUrl, null, null, conductorAiHttpClient);
                    case "cohere" ->
                            new CohereAIConfiguration(apiKey, baseUrl, conductorAiHttpClient);
                    case "grok" -> new GrokAIConfiguration(apiKey, baseUrl, conductorAiHttpClient);
                    case "huggingface" -> {
                        var c = new HuggingFaceConfiguration();
                        c.setApiKey(apiKey);
                        yield c;
                    }
                    case "perplexity" ->
                            new PerplexityAIConfiguration(apiKey, baseUrl, conductorAiHttpClient);
                    case "gemini", "google_gemini" -> null; // Handled below
                    default -> null;
                };

        if (config != null) {
            return config.get();
        }

        // Gemini uses the upstream configuration object so Conductor owns the concrete model path.
        String providerLower = provider.toLowerCase();
        if (providerLower.equals("gemini") || providerLower.equals("google_gemini")) {
            return createGeminiModel(apiKey);
        }

        return null;
    }

    /** Create a Gemini model using API key auth through the upstream Conductor configuration. */
    private AIModel createGeminiModel(String apiKey) {
        String projectId = resolveUserCredential("GOOGLE_CLOUD_PROJECT");
        var config = new GeminiVertexConfiguration();
        config.setApiKey(apiKey);
        config.setProjectId(projectId != null ? projectId : "google-ai-studio");
        config.setLocation("us-central1");
        config.setHttpClient(conductorAiHttpClient);
        return config.get();
    }
}
