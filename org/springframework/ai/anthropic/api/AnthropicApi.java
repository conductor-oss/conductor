/*
 * Copyright 2023-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.anthropic.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.ai.anthropic.api.AnthropicApi.ChatCompletionRequest.CacheControl;
import org.springframework.ai.anthropic.api.StreamHelper.ChatCompletionResponseBuilder;
import org.springframework.ai.model.ApiKey;
import org.springframework.ai.model.ChatModelDescription;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * The Anthropic API client.
 *
 * @author Christian Tzolov
 * @author Mariusz Bernacki
 * @author Thomas Vitale
 * @author Jihoon Kim
 * @author Alexandros Pappas
 * @author Jonghoon Park
 * @author Claudio Silva Junior
 * @author Filip Hrisafov
 * @author Soby Chacko
 * @author Austin Dase
 * @since 1.0.0
 */
public final class AnthropicApi {

	private static final Logger logger = LoggerFactory.getLogger(AnthropicApi.class);

	public static Builder builder() {
		return new Builder();
	}

	public static final String PROVIDER_NAME = AiProvider.ANTHROPIC.value();

	public static final String DEFAULT_BASE_URL = "https://api.anthropic.com";

	public static final String DEFAULT_MESSAGE_COMPLETIONS_PATH = "/v1/messages";

	public static final String DEFAULT_ANTHROPIC_VERSION = "2023-06-01";

	public static final String DEFAULT_ANTHROPIC_BETA_VERSION = "tools-2024-04-04,pdfs-2024-09-25,structured-outputs-2025-11-13";

	public static final String BETA_EXTENDED_CACHE_TTL = "extended-cache-ttl-2025-04-11";

	public static final String BETA_SKILLS = "skills-2025-10-02";

	public static final String BETA_FILES_API = "files-api-2025-04-14";

	public static final String BETA_CODE_EXECUTION = "code-execution-2025-08-25";

	public static final String CODE_EXECUTION_TOOL_TYPE = "code_execution_20250825";

	private static final String FILES_PATH = "/v1/files";

	private static final String HEADER_X_API_KEY = "x-api-key";

	private static final String HEADER_ANTHROPIC_VERSION = "anthropic-version";

	private static final String HEADER_ANTHROPIC_BETA = "anthropic-beta";

	private static final Predicate<String> SSE_DONE_PREDICATE = "[DONE]"::equals;

	private final String completionsPath;

	private final RestClient restClient;

	private final StreamHelper streamHelper = new StreamHelper();

	private final WebClient webClient;

	private final ApiKey apiKey;

	/**
	 * Create a new client api.
	 * @param baseUrl api base URL.
	 * @param completionsPath path to append to the base URL.
	 * @param anthropicApiKey Anthropic api Key.
	 * @param anthropicVersion Anthropic version.
	 * @param restClientBuilder RestClient builder.
	 * @param webClientBuilder WebClient builder.
	 * @param responseErrorHandler Response error handler.
	 * @param anthropicBetaFeatures Anthropic beta features.
	 */
	private AnthropicApi(String baseUrl, String completionsPath, ApiKey anthropicApiKey, String anthropicVersion,
			RestClient.Builder restClientBuilder, WebClient.Builder webClientBuilder,
			ResponseErrorHandler responseErrorHandler, String anthropicBetaFeatures) {

		Consumer<HttpHeaders> jsonContentHeaders = headers -> {
			headers.add(HEADER_ANTHROPIC_VERSION, anthropicVersion);
			headers.add(HEADER_ANTHROPIC_BETA, anthropicBetaFeatures);
			headers.setContentType(MediaType.APPLICATION_JSON);
		};

		this.completionsPath = completionsPath;
		this.apiKey = anthropicApiKey;

		this.restClient = restClientBuilder.clone()
			.baseUrl(baseUrl)
			.defaultHeaders(jsonContentHeaders)
			.defaultStatusHandler(responseErrorHandler)
			.build();

		this.webClient = webClientBuilder.clone()
			.baseUrl(baseUrl)
			.defaultHeaders(jsonContentHeaders)
			.defaultStatusHandler(HttpStatusCode::isError,
					resp -> resp.bodyToMono(String.class)
						.flatMap(it -> Mono.error(new RuntimeException(
								"Response exception, Status: [" + resp.statusCode() + "], Body:[" + it + "]"))))
			.build();
	}

	/**
	 * Creates a model response for the given chat conversation.
	 * @param chatRequest The chat completion request.
	 * @return Entity response with {@link ChatCompletionResponse} as a body and HTTP
	 * status code and headers.
	 */
	public ResponseEntity<ChatCompletionResponse> chatCompletionEntity(ChatCompletionRequest chatRequest) {
		return chatCompletionEntity(chatRequest, new LinkedMultiValueMap<>());
	}

	/**
	 * Creates a model response for the given chat conversation.
	 * @param chatRequest The chat completion request.
	 * @param additionalHttpHeader Additional HTTP headers.
	 * @return Entity response with {@link ChatCompletionResponse} as a body and HTTP
	 * status code and headers.
	 */
	public ResponseEntity<ChatCompletionResponse> chatCompletionEntity(ChatCompletionRequest chatRequest,
			MultiValueMap<String, String> additionalHttpHeader) {

		Assert.notNull(chatRequest, "The request body can not be null.");
		Assert.isTrue(!chatRequest.stream(), "Request must set the stream property to false.");
		Assert.notNull(additionalHttpHeader, "The additional HTTP headers can not be null.");

		// @formatter:off
		return this.restClient.post()
			.uri(this.completionsPath)
			.headers(headers -> {
				headers.addAll(additionalHttpHeader);
				addDefaultHeadersIfMissing(headers);
			})
			.body(chatRequest)
			.retrieve()
			.toEntity(ChatCompletionResponse.class);
		// @formatter:on
	}

	/**
	 * Creates a streaming chat response for the given chat conversation.
	 * @param chatRequest The chat completion request. Must have the stream property set
	 * to true.
	 * @return Returns a {@link Flux} stream from chat completion chunks.
	 */
	public Flux<ChatCompletionResponse> chatCompletionStream(ChatCompletionRequest chatRequest) {
		return chatCompletionStream(chatRequest, new HttpHeaders());
	}

	/**
	 * Creates a streaming chat response for the given chat conversation.
	 * @param chatRequest The chat completion request. Must have the stream property set
	 * to true.
	 * @param additionalHttpHeader Additional HTTP headers.
	 * @return Returns a {@link Flux} stream from chat completion chunks.
	 */
	public Flux<ChatCompletionResponse> chatCompletionStream(ChatCompletionRequest chatRequest,
			MultiValueMap<String, String> additionalHttpHeader) {

		Assert.notNull(chatRequest, "The request body can not be null.");
		Assert.isTrue(chatRequest.stream(), "Request must set the stream property to true.");
		Assert.notNull(additionalHttpHeader, "The additional HTTP headers can not be null.");

		AtomicBoolean isInsideTool = new AtomicBoolean(false);

		AtomicReference<ChatCompletionResponseBuilder> chatCompletionReference = new AtomicReference<>();

		// @formatter:off
		return this.webClient.post()
			.uri(this.completionsPath)
			.headers(headers -> {
				headers.addAll(additionalHttpHeader);
				addDefaultHeadersIfMissing(headers);
			}) // @formatter:off
			.body(Mono.just(chatRequest), ChatCompletionRequest.class)
			.retrieve()
			.bodyToFlux(String.class)
			.takeUntil(SSE_DONE_PREDICATE)
			.filter(SSE_DONE_PREDICATE.negate())
			.map(content -> ModelOptionsUtils.jsonToObject(content, StreamEvent.class))
			.filter(event -> event.type() != EventType.PING)
			// Detect if the chunk is part of a streaming function call.
			.map(event -> {
				logger.debug("Received event: {}", event);

				if (this.streamHelper.isToolUseStart(event)) {
					isInsideTool.set(true);
				}
				return event;
			})
			// Group all chunks belonging to the same function call.
			.windowUntil(event -> {
				if (isInsideTool.get() && this.streamHelper.isToolUseFinish(event)) {
					isInsideTool.set(false);
					return true;
				}
				return !isInsideTool.get();
			})
			// Merging the window chunks into a single chunk.
			.concatMapIterable(window -> {
				Mono<StreamEvent> monoChunk = window.reduce(new ToolUseAggregationEvent(),
						this.streamHelper::mergeToolUseEvents);
				return List.of(monoChunk);
			})
			.flatMap(mono -> mono)
			.map(event -> this.streamHelper.eventToChatCompletionResponse(event, chatCompletionReference))
			.filter(chatCompletionResponse -> chatCompletionResponse.type() != null);
	}

	// Files API Methods

	/**
	 * Get metadata for a specific file generated by Skills or uploaded via Files API.
	 * @param fileId The ID of the file (format: file_*)
	 * @return File metadata including filename, size, mime type, and expiration
	 */
	public FileMetadata getFileMetadata(String fileId) {
		Assert.hasText(fileId, "File ID cannot be empty");

		return this.restClient.get()
			.uri(FILES_PATH + "/{id}", fileId)
			.headers(headers -> {
				addDefaultHeadersIfMissing(headers);
				// Append files-api beta to existing beta headers if not already present
				String existingBeta = headers.getFirst(HEADER_ANTHROPIC_BETA);
				if (existingBeta != null && !existingBeta.contains(BETA_FILES_API)) {
					headers.set(HEADER_ANTHROPIC_BETA, existingBeta + "," + BETA_FILES_API);
				}
				else if (existingBeta == null) {
					headers.set(HEADER_ANTHROPIC_BETA, BETA_FILES_API);
				}
			})
			.retrieve()
			.body(FileMetadata.class);
	}

	/**
	 * Download file content by ID.
	 * @param fileId The ID of the file (format: file_*)
	 * @return File content as bytes
	 */
	public byte[] downloadFile(String fileId) {
		Assert.hasText(fileId, "File ID cannot be empty");

		return this.restClient.get()
			.uri(FILES_PATH + "/{id}/content", fileId)
			.headers(headers -> {
				addDefaultHeadersIfMissing(headers);
				// Append files-api beta to existing beta headers if not already present
				String existingBeta = headers.getFirst(HEADER_ANTHROPIC_BETA);
				if (existingBeta != null && !existingBeta.contains(BETA_FILES_API)) {
					headers.set(HEADER_ANTHROPIC_BETA, existingBeta + "," + BETA_FILES_API);
				}
				else if (existingBeta == null) {
					headers.set(HEADER_ANTHROPIC_BETA, BETA_FILES_API);
				}
			})
			.retrieve()
			.body(byte[].class);
	}

	private void addDefaultHeadersIfMissing(HttpHeaders headers) {
		if (!headers.containsKey(HEADER_X_API_KEY)) {
			String apiKeyValue = this.apiKey.getValue();
			if (StringUtils.hasText(apiKeyValue)) {
				headers.add(HEADER_X_API_KEY, apiKeyValue);
			}
		}
	}

	/**
	 * Check the <a href="https://docs.anthropic.com/claude/docs/models-overview">Models
	 * overview</a> and <a href=
	 * "https://docs.anthropic.com/claude/docs/models-overview#model-comparison">model
	 * comparison</a> for additional details and options.
	 */
	public enum ChatModel implements ChatModelDescription {

		// @formatter:off
		/**
		 * The claude-sonnet-4-5 model.
		 */
		CLAUDE_SONNET_4_5("claude-sonnet-4-5"),

		/**
		 * The claude-opus-4-5 model.
		 */
		CLAUDE_OPUS_4_5("claude-opus-4-5"),

		/**
		 * The claude-haiku-4-5 model.
		 */
		CLAUDE_HAIKU_4_5("claude-haiku-4-5"),

		/**
		 * The claude-opus-4-1 model.
		 */
		CLAUDE_OPUS_4_1("claude-opus-4-1"),

		/**
		 * The claude-opus-4-0 model.
		 */
		CLAUDE_OPUS_4_0("claude-opus-4-0"),

		/**
		 * The claude-sonnet-4-0 model.
		 */
		CLAUDE_SONNET_4_0("claude-sonnet-4-0"),

		/**
		 * The claude-3-7-sonnet-latest model.
		 */
		CLAUDE_3_7_SONNET("claude-3-7-sonnet-latest"),

		/**
		 * The claude-3-5-sonnet-latest model.(Deprecated on October 28, 2025)
		 */
		CLAUDE_3_5_SONNET("claude-3-5-sonnet-latest"),

		/**
		 * The CLAUDE_3_OPUS
		 */
		CLAUDE_3_OPUS("claude-3-opus-latest"),

		/**
		 * The CLAUDE_3_SONNET (Deprecated. To be removed on July 21, 2025)
		 */
		CLAUDE_3_SONNET("claude-3-sonnet-20240229"),

		/**
		 * The CLAUDE 3.5 HAIKU
		 */
		CLAUDE_3_5_HAIKU("claude-3-5-haiku-latest"),

		/**
		 * The CLAUDE_3_HAIKU
		 */
		CLAUDE_3_HAIKU("claude-3-haiku-20240307");

		// @formatter:on

		private final String value;

		ChatModel(String value) {
			this.value = value;
		}

		/**
		 * Get the value of the model.
		 * @return The value of the model.
		 */
		public String getValue() {
			return this.value;
		}

		/**
		 * Get the name of the model.
		 * @return The name of the model.
		 */
		@Override
		public String getName() {
			return this.value;
		}

	}

	/**
	 * The role of the author of this message.
	 */
	public enum Role {

		// @formatter:off
		/**
		 * The user role.
		  */
		@JsonProperty("user")
		USER,

		/**
		 * The assistant role.
		 */
		@JsonProperty("assistant")
		ASSISTANT
		// @formatter:on

	}

	/**
	 * The thinking type.
	 */
	public enum ThinkingType {

		/**
		 * Enabled thinking type.
		 */
		@JsonProperty("enabled")
		ENABLED,

		/**
		 * Disabled thinking type.
		 */
		@JsonProperty("disabled")
		DISABLED

	}

	/**
	 * The event type of the streamed chunk.
	 */
	public enum EventType {

		/**
		 * Message start event. Contains a Message object with empty content.
		 */
		@JsonProperty("message_start")
		MESSAGE_START,

		/**
		 * Message delta event, indicating top-level changes to the final Message object.
		 */
		@JsonProperty("message_delta")
		MESSAGE_DELTA,

		/**
		 * A final message stop event.
		 */
		@JsonProperty("message_stop")
		MESSAGE_STOP,

		/**
		 * Content block start event.
		 */
		@JsonProperty("content_block_start")
		CONTENT_BLOCK_START,

		/**
		 * Content block delta event.
		 */
		@JsonProperty("content_block_delta")
		CONTENT_BLOCK_DELTA,

		/**
		 * A final content block stop event.
		 */
		@JsonProperty("content_block_stop")
		CONTENT_BLOCK_STOP,

		/**
		 * Error event.
		 */
		@JsonProperty("error")
		ERROR,

		/**
		 * Ping event.
		 */
		@JsonProperty("ping")
		PING,

		/**
		 * Artificially created event to aggregate tool use events.
		 */
		TOOL_USE_AGGREGATE

	}

	/**
	 * Types of Claude Skills.
	 */
	public enum SkillType {

		/**
		 * Pre-built Anthropic skills (xlsx, pptx, docx, pdf).
		 */
		@JsonProperty("anthropic")
		ANTHROPIC("anthropic"),

		/**
		 * Custom user-created skills.
		 */
		@JsonProperty("custom")
		CUSTOM("custom");

		private final String value;

		SkillType(String value) {
			this.value = value;
		}

		public String getValue() {
			return this.value;
		}

	}

	/**
	 * Pre-built Anthropic Skills for document generation.
	 */
	public enum AnthropicSkill {

		/**
		 * Excel spreadsheet generation skill.
		 */
		XLSX("xlsx", "Creates Excel spreadsheets"),

		/**
		 * PowerPoint presentation generation skill.
		 */
		PPTX("pptx", "Creates PowerPoint presentations"),

		/**
		 * Word document generation skill.
		 */
		DOCX("docx", "Creates Word documents"),

		/**
		 * PDF document generation skill.
		 */
		PDF("pdf", "Creates PDF documents");

		private final String skillId;

		private final String description;

		AnthropicSkill(String skillId, String description) {
			this.skillId = skillId;
			this.description = description;
		}

		public String getSkillId() {
			return this.skillId;
		}

		public String getDescription() {
			return this.description;
		}

		/**
		 * Convert to a Skill record with latest version.
		 * @return Skill record
		 */
		public Skill toSkill() {
			return new Skill(SkillType.ANTHROPIC, this.skillId, "latest");
		}

		/**
		 * Convert to a Skill record with specific version.
		 * @param version Version string
		 * @return Skill record
		 */
		public Skill toSkill(String version) {
			return new Skill(SkillType.ANTHROPIC, this.skillId, version);
		}

	}

	/**
	 * Represents a Claude Skill - either pre-built Anthropic skill or custom skill.
	 * Skills are collections of instructions, scripts, and resources that extend Claude's
	 * capabilities for specific domains.
	 *
	 * @param type Skill type - ANTHROPIC for pre-built skills or CUSTOM for user-created.
	 * @param skillId Skill identifier - short name for Anthropic skills (e.g., "xlsx",
	 * "pptx") or custom skill ID.
	 * @param version Optional version string (e.g., "latest", "20251013"). Defaults to
	 * "latest".
	 */
	@JsonInclude(Include.NON_NULL)
	public record Skill(@JsonProperty("type") SkillType type, @JsonProperty("skill_id") String skillId,
			@JsonProperty("version") String version) {

		/**
		 * Create a Skill with default "latest" version.
		 * @param type Skill type
		 * @param skillId Skill ID
		 */
		public Skill(SkillType type, String skillId) {
			this(type, skillId, "latest");
		}

		public static SkillBuilder builder() {
			return new SkillBuilder();
		}

		public static final class SkillBuilder {

			private SkillType type;

			private String skillId;

			private String version = "latest";

			public SkillBuilder type(SkillType type) {
				this.type = type;
				return this;
			}

			public SkillBuilder skillId(String skillId) {
				this.skillId = skillId;
				return this;
			}

			public SkillBuilder version(String version) {
				this.version = version;
				return this;
			}

			public Skill build() {
				Assert.notNull(this.type, "Skill type cannot be null");
				Assert.hasText(this.skillId, "Skill ID cannot be empty");
				return new Skill(this.type, this.skillId, this.version);
			}

		}

	}

	/**
	 * Container for Claude Skills in a chat completion request. Maximum of 8 skills per
	 * request.
	 *
	 * @param skills List of skills to make available
	 */
	@JsonInclude(Include.NON_NULL)
	public record SkillContainer(@JsonProperty("skills") List<Skill> skills) {

		public SkillContainer {
			Assert.notNull(skills, "Skills list cannot be null");
			Assert.isTrue(skills.size() <= 8, "Maximum of 8 skills per request");
		}

		public static SkillContainerBuilder builder() {
			return new SkillContainerBuilder();
		}

		public static final class SkillContainerBuilder {

			private final List<Skill> skills = new ArrayList<>();

			public SkillContainerBuilder skill(Skill skill) {
				Assert.notNull(skill, "Skill cannot be null");
				this.skills.add(skill);
				return this;
			}

			public SkillContainerBuilder skill(SkillType type, String skillId) {
				return skill(new Skill(type, skillId));
			}

			public SkillContainerBuilder skill(SkillType type, String skillId, String version) {
				return skill(new Skill(type, skillId, version));
			}

			public SkillContainerBuilder anthropicSkill(AnthropicSkill skill) {
				return skill(skill.toSkill());
			}

			public SkillContainerBuilder anthropicSkill(AnthropicSkill skill, String version) {
				return skill(skill.toSkill(version));
			}

			public SkillContainerBuilder customSkill(String skillId) {
				return skill(new Skill(SkillType.CUSTOM, skillId));
			}

			public SkillContainerBuilder customSkill(String skillId, String version) {
				return skill(new Skill(SkillType.CUSTOM, skillId, version));
			}

			public SkillContainerBuilder skills(List<Skill> skills) {
				Assert.notNull(skills, "Skills list cannot be null");
				this.skills.addAll(skills);
				return this;
			}

			public SkillContainer build() {
				return new SkillContainer(new ArrayList<>(this.skills));
			}

		}

	}

	/**
	 * File metadata returned from the Files API.
	 *
	 * @param id Unique file identifier (format: file_*)
	 * @param filename Original filename
	 * @param size File size in bytes
	 * @param mimeType MIME type of the file
	 * @param createdAt Creation timestamp
	 * @param expiresAt Expiration timestamp
	 */
	@JsonInclude(Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record FileMetadata(@JsonProperty("id") String id, @JsonProperty("filename") String filename,
			@JsonProperty("size") Long size, @JsonProperty("mime_type") String mimeType,
			@JsonProperty("created_at") String createdAt, @JsonProperty("expires_at") String expiresAt) {
	}

	@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type",
			visible = true)
	@JsonSubTypes({ @JsonSubTypes.Type(value = ContentBlockStartEvent.class, name = "content_block_start"),
			@JsonSubTypes.Type(value = ContentBlockDeltaEvent.class, name = "content_block_delta"),
			@JsonSubTypes.Type(value = ContentBlockStopEvent.class, name = "content_block_stop"),
			@JsonSubTypes.Type(value = PingEvent.class, name = "ping"),
			@JsonSubTypes.Type(value = ErrorEvent.class, name = "error"),
			@JsonSubTypes.Type(value = MessageStartEvent.class, name = "message_start"),
			@JsonSubTypes.Type(value = MessageDeltaEvent.class, name = "message_delta"),
			@JsonSubTypes.Type(value = MessageStopEvent.class, name = "message_stop") })
	public interface StreamEvent {

		@JsonProperty("type")
		EventType type();

	}

	/**
	 * Chat completion request object.
	 *
	 * @param model The model that will complete your prompt. See the list of
	 * <a href="https://docs.anthropic.com/claude/docs/models-overview">models</a> for
	 * additional details and options.
	 * @param messages Input messages.
	 * @param system System prompt. Can be a String (for compatibility) or a
	 * List&lt;ContentBlock&gt; (for caching support). A system prompt is a way of
	 * providing context and instructions to Claude, such as specifying a particular goal
	 * or role. See our
	 * <a href="https://docs.anthropic.com/claude/docs/system-prompts">guide</a> to system
	 * prompts.
	 * @param maxTokens The maximum number of tokens to generate before stopping. Note
	 * that our models may stop before reaching this maximum. This parameter only
	 * specifies the absolute maximum number of tokens to generate. Different models have
	 * different maximum values for this parameter.
	 * @param metadata An object describing metadata about the request.
	 * @param stopSequences Custom text sequences that will cause the model to stop
	 * generating. Our models will normally stop when they have naturally completed their
	 * turn, which will result in a response stop_reason of "end_turn". If you want the
	 * model to stop generating when it encounters custom strings of text, you can use the
	 * stop_sequences parameter. If the model encounters one of the custom sequences, the
	 * response stop_reason value will be "stop_sequence" and the response stop_sequence
	 * value will contain the matched stop sequence.
	 * @param stream Whether to incrementally stream the response using server-sent
	 * events.
	 * @param temperature Amount of randomness injected into the response.Defaults to 1.0.
	 * Ranges from 0.0 to 1.0. Use temperature closer to 0.0 for analytical / multiple
	 * choice, and closer to 1.0 for creative and generative tasks. Note that even with
	 * temperature of 0.0, the results will not be fully deterministic.
	 * @param topP Use nucleus sampling. In nucleus sampling, we compute the cumulative
	 * distribution over all the options for each subsequent token in decreasing
	 * probability order and cut it off once it reaches a particular probability specified
	 * by top_p. You should either alter temperature or top_p, but not both. Recommended
	 * for advanced use cases only. You usually only need to use temperature.
	 * @param topK Only sample from the top K options for each subsequent token. Used to
	 * remove "long tail" low probability responses. Learn more technical details here.
	 * Recommended for advanced use cases only. You usually only need to use temperature.
	 * @param tools Definitions of tools that the model may use. If provided the model may
	 * return tool_use content blocks that represent the model's use of those tools. You
	 * can then run those tools using the tool input generated by the model and then
	 * optionally return results back to the model using tool_result content blocks.
	 * @param toolChoice How the model should use the provided tools. The model can use a
	 * specific tool, any available tool, decide by itself, or not use tools at all.
	 * @param thinking Configuration for the model's thinking mode. When enabled, the
	 * model can perform more in-depth reasoning before responding to a query.
	 */
	@JsonInclude(Include.NON_NULL)
	public record ChatCompletionRequest(
	// @formatter:off
		@JsonProperty("model") String model,
		@JsonProperty("messages") List<AnthropicMessage> messages,
		@JsonProperty("system") Object system,
		@JsonProperty("max_tokens") Integer maxTokens,
		@JsonProperty("metadata") Metadata metadata,
		@JsonProperty("stop_sequences") List<String> stopSequences,
		@JsonProperty("stream") Boolean stream,
		@JsonProperty("temperature") Double temperature,
		@JsonProperty("top_p") Double topP,
		@JsonProperty("top_k") Integer topK,
		@JsonProperty("tools") List<Tool> tools,
		@JsonProperty("tool_choice") ToolChoice toolChoice,
		@JsonProperty("thinking") ThinkingConfig thinking,
		@JsonProperty("output_format") OutputFormat outputFormat,
		@JsonProperty("container") SkillContainer container) {
		// @formatter:on

		public ChatCompletionRequest(String model, List<AnthropicMessage> messages, Object system, Integer maxTokens,
				Double temperature, Boolean stream) {
			this(model, messages, system, maxTokens, null, null, stream, temperature, null, null, null, null, null,
					null, null);
		}

		public ChatCompletionRequest(String model, List<AnthropicMessage> messages, Object system, Integer maxTokens,
				List<String> stopSequences, Double temperature, Boolean stream) {
			this(model, messages, system, maxTokens, null, stopSequences, stream, temperature, null, null, null, null,
					null, null, null);
		}

		public static ChatCompletionRequestBuilder builder() {
			return new ChatCompletionRequestBuilder();
		}

		public static ChatCompletionRequestBuilder from(ChatCompletionRequest request) {
			return new ChatCompletionRequestBuilder(request);
		}

		@JsonInclude(Include.NON_NULL)
		public record OutputFormat(@JsonProperty("type") String type,
				@JsonProperty("schema") Map<String, Object> schema) {

			public OutputFormat(String jsonSchema) {
				this("json_schema", ModelOptionsUtils.jsonToMap(jsonSchema));
			}
		}

		/**
		 * Metadata about the request.
		 *
		 * @param userId An external identifier for the user who is associated with the
		 * request. This should be a uuid, hash value, or other opaque identifier.
		 * Anthropic may use this id to help detect abuse. Do not include any identifying
		 * information such as name, email address, or phone number.
		 */
		@JsonInclude(Include.NON_NULL)
		public record Metadata(@JsonProperty("user_id") String userId) {

		}

		/**
		 * @param type is the cache type supported by anthropic. <a href=
		 * "https://docs.anthropic.com/en/docs/build-with-claude/prompt-caching#cache-limitations">Doc</a>
		 */
		@JsonInclude(Include.NON_NULL)
		public record CacheControl(@JsonProperty("type") String type, @JsonProperty("ttl") String ttl) {

			public CacheControl(String type) {
				this(type, "5m");
			}
		}

		/**
		 * Configuration for the model's thinking mode.
		 *
		 * @param type The type of thinking mode. Currently, "enabled" is supported.
		 * @param budgetTokens The token budget available for the thinking process. Must
		 * be ≥1024 and less than max_tokens.
		 */
		@JsonInclude(Include.NON_NULL)
		public record ThinkingConfig(@JsonProperty("type") ThinkingType type,
				@JsonProperty("budget_tokens") Integer budgetTokens) {
		}

	}

	public static final class ChatCompletionRequestBuilder {

		private String model;

		private List<AnthropicMessage> messages;

		private Object system;

		private Integer maxTokens;

		private ChatCompletionRequest.Metadata metadata;

		private List<String> stopSequences;

		private Boolean stream = false;

		private Double temperature;

		private Double topP;

		private Integer topK;

		private List<Tool> tools;

		private ToolChoice toolChoice;

		private ChatCompletionRequest.ThinkingConfig thinking;

		private SkillContainer container;

		private ChatCompletionRequest.OutputFormat outputFormat;

		private ChatCompletionRequestBuilder() {
		}

		private ChatCompletionRequestBuilder(ChatCompletionRequest request) {
			this.model = request.model;
			this.messages = request.messages;
			this.system = request.system;
			this.maxTokens = request.maxTokens;
			this.metadata = request.metadata;
			this.stopSequences = request.stopSequences;
			this.stream = request.stream;
			this.temperature = request.temperature;
			this.topP = request.topP;
			this.topK = request.topK;
			this.tools = request.tools;
			this.toolChoice = request.toolChoice;
			this.thinking = request.thinking;
			this.container = request.container;
			this.outputFormat = request.outputFormat;
		}

		public ChatCompletionRequestBuilder model(ChatModel model) {
			this.model = model.getValue();
			return this;
		}

		public ChatCompletionRequestBuilder model(String model) {
			this.model = model;
			return this;
		}

		public ChatCompletionRequestBuilder messages(List<AnthropicMessage> messages) {
			this.messages = messages;
			return this;
		}

		public ChatCompletionRequestBuilder system(Object system) {
			this.system = system;
			return this;
		}

		public ChatCompletionRequestBuilder maxTokens(Integer maxTokens) {
			this.maxTokens = maxTokens;
			return this;
		}

		public ChatCompletionRequestBuilder metadata(ChatCompletionRequest.Metadata metadata) {
			this.metadata = metadata;
			return this;
		}

		public ChatCompletionRequestBuilder stopSequences(List<String> stopSequences) {
			this.stopSequences = stopSequences;
			return this;
		}

		public ChatCompletionRequestBuilder stream(Boolean stream) {
			this.stream = stream;
			return this;
		}

		public ChatCompletionRequestBuilder temperature(Double temperature) {
			this.temperature = temperature;
			return this;
		}

		public ChatCompletionRequestBuilder topP(Double topP) {
			this.topP = topP;
			return this;
		}

		public ChatCompletionRequestBuilder topK(Integer topK) {
			this.topK = topK;
			return this;
		}

		public ChatCompletionRequestBuilder tools(List<Tool> tools) {
			this.tools = tools;
			return this;
		}

		public ChatCompletionRequestBuilder toolChoice(ToolChoice toolChoice) {
			this.toolChoice = toolChoice;
			return this;
		}

		public ChatCompletionRequestBuilder thinking(ChatCompletionRequest.ThinkingConfig thinking) {
			this.thinking = thinking;
			return this;
		}

		public ChatCompletionRequestBuilder thinking(ThinkingType type, Integer budgetTokens) {
			this.thinking = new ChatCompletionRequest.ThinkingConfig(type, budgetTokens);
			return this;
		}

		public ChatCompletionRequestBuilder container(SkillContainer container) {
			this.container = container;
			return this;
		}

		public ChatCompletionRequestBuilder skills(List<Skill> skills) {
			if (skills != null && !skills.isEmpty()) {
				this.container = new SkillContainer(skills);
			}
			return this;
		}

		public ChatCompletionRequestBuilder outputFormat(ChatCompletionRequest.OutputFormat outputFormat) {
			this.outputFormat = outputFormat;
			return this;
		}

		public ChatCompletionRequest build() {
			return new ChatCompletionRequest(this.model, this.messages, this.system, this.maxTokens, this.metadata,
					this.stopSequences, this.stream, this.temperature, this.topP, this.topK, this.tools,
					this.toolChoice, this.thinking, this.outputFormat, this.container);
		}

	}

	///////////////////////////////////////
	/// ERROR EVENT
	///////////////////////////////////////

	/**
	 * Input messages.
	 *
	 * Our models are trained to operate on alternating user and assistant conversational
	 * turns. When creating a new Message, you specify the prior conversational turns with
	 * the messages parameter, and the model then generates the next Message in the
	 * conversation. Each input message must be an object with a role and content. You can
	 * specify a single user-role message, or you can include multiple user and assistant
	 * messages. The first message must always use the user role. If the final message
	 * uses the assistant role, the response content will continue immediately from the
	 * content in that message. This can be used to constrain part of the model's
	 * response.
	 *
	 * @param content The contents of the message. Can be of one of String or
	 * MultiModalContent.
	 * @param role The role of the messages author. Could be one of the {@link Role}
	 * types.
	 */
	@JsonInclude(Include.NON_NULL)
	public record AnthropicMessage(
	// @formatter:off
		@JsonProperty("content") List<ContentBlock> content,
		@JsonProperty("role") Role role) {
		// @formatter:on
	}

	/**
	 * Citations configuration for document ContentBlocks.
	 */
	@JsonInclude(Include.NON_NULL)
	public record CitationsConfig(@JsonProperty("enabled") Boolean enabled) {
	}

	/**
	 * Citation response structure from Anthropic API. Maps to the actual API response
	 * format for citations. Contains location information that varies by document type:
	 * character indices for plain text, page numbers for PDFs, or content block indices
	 * for custom content.
	 *
	 * @param type The citation location type ("char_location", "page_location", or
	 * "content_block_location")
	 * @param citedText The text that was cited from the document
	 * @param documentIndex The index of the document that was cited (0-based)
	 * @param documentTitle The title of the document that was cited
	 * @param startCharIndex The starting character index for "char_location" type
	 * (0-based, inclusive)
	 * @param endCharIndex The ending character index for "char_location" type (exclusive)
	 * @param startPageNumber The starting page number for "page_location" type (1-based,
	 * inclusive)
	 * @param endPageNumber The ending page number for "page_location" type (exclusive)
	 * @param startBlockIndex The starting content block index for
	 * "content_block_location" type (0-based, inclusive)
	 * @param endBlockIndex The ending content block index for "content_block_location"
	 * type (exclusive)
	 */
	@JsonInclude(Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record CitationResponse(@JsonProperty("type") String type, @JsonProperty("cited_text") String citedText,
			@JsonProperty("document_index") Integer documentIndex, @JsonProperty("document_title") String documentTitle,

			// For char_location type
			@JsonProperty("start_char_index") Integer startCharIndex,
			@JsonProperty("end_char_index") Integer endCharIndex,

			// For page_location type
			@JsonProperty("start_page_number") Integer startPageNumber,
			@JsonProperty("end_page_number") Integer endPageNumber,

			// For content_block_location type
			@JsonProperty("start_block_index") Integer startBlockIndex,
			@JsonProperty("end_block_index") Integer endBlockIndex) {
	}

	/**
	 * The content block of the message.
	 *
	 * @param type the content type can be "text", "image", "tool_use", "tool_result" or
	 * "text_delta".
	 * @param source The source of the media content. Applicable for "image" types only.
	 * @param text The text of the message. Applicable for "text" types only.
	 * @param index The index of the content block. Applicable only for streaming
	 * responses.
	 * @param id The id of the tool use. Applicable only for tool_use response.
	 * @param name The name of the tool use. Applicable only for tool_use response.
	 * @param input The input of the tool use. Applicable only for tool_use response.
	 * @param toolUseId The id of the tool use. Applicable only for tool_result response.
	 * @param content The content of the tool result. Applicable only for tool_result
	 * response.
	 */
	@JsonInclude(Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record ContentBlock(
	// @formatter:off
		@JsonProperty("type") Type type,
		@JsonProperty("source") Source source,
		@JsonProperty("text") String text,

		// applicable only for streaming responses.
		@JsonProperty("index") Integer index,

		// tool_use response only
		@JsonProperty("id") String id,
		@JsonProperty("name") String name,
		@JsonProperty("input") Map<String, Object> input,

		// tool_result response only
		@JsonProperty("tool_use_id") String toolUseId,
		@JsonProperty("content") Object content,

		// Thinking only
		@JsonProperty("signature") String signature,
		@JsonProperty("thinking") String thinking,

		// Redacted Thinking only
		@JsonProperty("data") String data,

		// cache object
		@JsonProperty("cache_control") CacheControl cacheControl,

		// Citation fields
		@JsonProperty("title") String title,
		@JsonProperty("context") String context,
		@JsonProperty("citations") Object citations, // Can be CitationsConfig for requests or List<CitationResponse> for responses

		// File fields (for Skills-generated files)
		@JsonProperty("file_id") String fileId,
		@JsonProperty("filename") String filename
	) {
		// @formatter:on

		/**
		 * Create content block
		 * @param mediaType The media type of the content.
		 * @param data The content data.
		 */
		public ContentBlock(String mediaType, String data) {
			this(new Source(mediaType, data));
		}

		/**
		 * Create content block
		 * @param type The type of the content.
		 * @param source The source of the content.
		 */
		public ContentBlock(Type type, Source source) {
			this(type, source, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
					null);
		}

		/**
		 * Create content block
		 * @param source The source of the content.
		 */
		public ContentBlock(Source source) {
			this(Type.IMAGE, source, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
					null, null);
		}

		/**
		 * Create content block
		 * @param text The text of the content.
		 */
		public ContentBlock(String text) {
			this(Type.TEXT, null, text, null, null, null, null, null, null, null, null, null, null, null, null, null,
					null, null);
		}

		public ContentBlock(String text, CacheControl cache) {
			this(Type.TEXT, null, text, null, null, null, null, null, null, null, null, null, cache, null, null, null,
					null, null);
		}

		// Tool result
		/**
		 * Create content block
		 * @param type The type of the content.
		 * @param toolUseId The id of the tool use.
		 * @param content The content of the tool result.
		 */
		public ContentBlock(Type type, String toolUseId, String content) {
			this(type, null, null, null, null, null, null, toolUseId, content, null, null, null, null, null, null, null,
					null, null);
		}

		/**
		 * Create content block
		 * @param type The type of the content.
		 * @param source The source of the content.
		 * @param text The text of the content.
		 * @param index The index of the content block.
		 */
		public ContentBlock(Type type, Source source, String text, Integer index) {
			this(type, source, text, index, null, null, null, null, null, null, null, null, null, null, null, null,
					null, null);
		}

		// Tool use input JSON delta streaming
		/**
		 * Create content block
		 * @param type The type of the content.
		 * @param id The id of the tool use.
		 * @param name The name of the tool use.
		 * @param input The input of the tool use.
		 */
		public ContentBlock(Type type, String id, String name, Map<String, Object> input) {
			this(type, null, null, null, id, name, input, null, null, null, null, null, null, null, null, null, null,
					null);
		}

		/**
		 * Create a document ContentBlock with citations and optional caching.
		 * @param source The document source
		 * @param title Optional document title
		 * @param context Optional document context
		 * @param citationsEnabled Whether citations are enabled
		 * @param cacheControl Optional cache control (can be null)
		 */
		public ContentBlock(Source source, String title, String context, boolean citationsEnabled,
				CacheControl cacheControl) {
			this(Type.DOCUMENT, source, null, null, null, null, null, null, null, null, null, null, cacheControl, title,
					context, citationsEnabled ? new CitationsConfig(true) : null, null, null);
		}

		public static ContentBlockBuilder from(ContentBlock contentBlock) {
			return new ContentBlockBuilder(contentBlock);
		}

		/**
		 * Returns the content as a String if it is a String, null otherwise.
		 * <p>
		 * Note: The {@link #content()} field was changed from {@code String} to
		 * {@code Object} to support Skills responses which may return complex nested
		 * structures. If you are using the low-level API and previously relied on
		 * {@code content()} returning a String, use this method instead.
		 * @return the content as String, or null if content is not a String
		 */
		public String contentAsString() {
			return this.content instanceof String s ? s : null;
		}

		/**
		 * The ContentBlock type.
		 */
		public enum Type {

			/**
			 * Tool request
			 */
			@JsonProperty("tool_use")
			TOOL_USE("tool_use"),

			/**
			 * Send tool result back to LLM.
			 */
			@JsonProperty("tool_result")
			TOOL_RESULT("tool_result"),

			/**
			 * Text message.
			 */
			@JsonProperty("text")
			TEXT("text"),

			/**
			 * Text delta message. Returned from the streaming response.
			 */
			@JsonProperty("text_delta")
			TEXT_DELTA("text_delta"),

			/**
			 * When using extended thinking with streaming enabled, you’ll receive
			 * thinking content via thinking_delta events. These deltas correspond to the
			 * thinking field of the thinking content blocks.
			 */
			@JsonProperty("thinking_delta")
			THINKING_DELTA("thinking_delta"),

			/**
			 * For thinking content, a special signature_delta event is sent just before
			 * the content_block_stop event. This signature is used to verify the
			 * integrity of the thinking block.
			 */
			@JsonProperty("signature_delta")
			SIGNATURE_DELTA("signature_delta"),

			/**
			 * Tool use input partial JSON delta streaming.
			 */
			@JsonProperty("input_json_delta")
			INPUT_JSON_DELTA("input_json_delta"),

			/**
			 * Image message.
			 */
			@JsonProperty("image")
			IMAGE("image"),

			/**
			 * Document message.
			 */
			@JsonProperty("document")
			DOCUMENT("document"),

			/**
			 * Thinking message.
			 */
			@JsonProperty("thinking")
			THINKING("thinking"),

			/**
			 * Redacted Thinking message.
			 */
			@JsonProperty("redacted_thinking")
			REDACTED_THINKING("redacted_thinking"),

			/**
			 * File content block representing a file generated by Skills. Used in
			 * {@link org.springframework.ai.anthropic.SkillsResponseHelper} to extract
			 * file IDs for downloading generated documents.
			 */
			@JsonProperty("file")
			FILE("file"),

			/**
			 * Bash code execution tool result returned in Skills responses. Observed in
			 * actual API responses where file IDs are nested within this content block.
			 * Required for JSON deserialization.
			 */
			@JsonProperty("bash_code_execution_tool_result")
			BASH_CODE_EXECUTION_TOOL_RESULT("bash_code_execution_tool_result"),

			/**
			 * Text editor code execution tool result returned in Skills responses.
			 * Observed in actual API responses. Required for JSON deserialization.
			 */
			@JsonProperty("text_editor_code_execution_tool_result")
			TEXT_EDITOR_CODE_EXECUTION_TOOL_RESULT("text_editor_code_execution_tool_result"),

			/**
			 * Server-side tool use returned in Skills responses. Observed in actual API
			 * responses when Skills invoke server-side tools. Required for JSON
			 * deserialization.
			 */
			@JsonProperty("server_tool_use")
			SERVER_TOOL_USE("server_tool_use");

			public final String value;

			Type(String value) {
				this.value = value;
			}

			/**
			 * Get the value of the type.
			 * @return The value of the type.
			 */
			public String getValue() {
				return this.value;
			}

		}

		/**
		 * The source of the media content. (Applicable for "image" types only)
		 *
		 * @param type The type of the media content. Only "base64" is supported at the
		 * moment.
		 * @param mediaType The media type of the content. For example, "image/png" or
		 * "image/jpeg".
		 * @param data The base64-encoded data of the content.
		 */
		@JsonInclude(Include.NON_NULL)
		public record Source(
		// @formatter:off
			@JsonProperty("type") String type,
			@JsonProperty("media_type") String mediaType,
			@JsonProperty("data") String data,
			@JsonProperty("url") String url,
			@JsonProperty("content") List<ContentBlock> content) {
			// @formatter:on

			/**
			 * Create source
			 * @param mediaType The media type of the content.
			 * @param data The content data.
			 */
			public Source(String mediaType, String data) {
				this("base64", mediaType, data, null, null);
			}

			public Source(String url) {
				this("url", null, null, url, null);
			}

			public Source(List<ContentBlock> content) {
				this("content", null, null, null, content);
			}

		}

		public static class ContentBlockBuilder {

			private Type type;

			private Source source;

			private String text;

			private Integer index;

			private String id;

			private String name;

			private Map<String, Object> input;

			private String toolUseId;

			private Object content;

			private String signature;

			private String thinking;

			private String data;

			private CacheControl cacheControl;

			private String title;

			private String context;

			private Object citations;

			private String fileId;

			private String filename;

			public ContentBlockBuilder(ContentBlock contentBlock) {
				this.type = contentBlock.type;
				this.source = contentBlock.source;
				this.text = contentBlock.text;
				this.index = contentBlock.index;
				this.id = contentBlock.id;
				this.name = contentBlock.name;
				this.input = contentBlock.input;
				this.toolUseId = contentBlock.toolUseId;
				this.content = contentBlock.content;
				this.signature = contentBlock.signature;
				this.thinking = contentBlock.thinking;
				this.data = contentBlock.data;
				this.cacheControl = contentBlock.cacheControl;
				this.title = contentBlock.title;
				this.context = contentBlock.context;
				this.citations = contentBlock.citations;
				this.fileId = contentBlock.fileId;
				this.filename = contentBlock.filename;
			}

			public ContentBlockBuilder type(Type type) {
				this.type = type;
				return this;
			}

			public ContentBlockBuilder source(Source source) {
				this.source = source;
				return this;
			}

			public ContentBlockBuilder text(String text) {
				this.text = text;
				return this;
			}

			public ContentBlockBuilder index(Integer index) {
				this.index = index;
				return this;
			}

			public ContentBlockBuilder id(String id) {
				this.id = id;
				return this;
			}

			public ContentBlockBuilder name(String name) {
				this.name = name;
				return this;
			}

			public ContentBlockBuilder input(Map<String, Object> input) {
				this.input = input;
				return this;
			}

			public ContentBlockBuilder toolUseId(String toolUseId) {
				this.toolUseId = toolUseId;
				return this;
			}

			public ContentBlockBuilder content(Object content) {
				this.content = content;
				return this;
			}

			public ContentBlockBuilder signature(String signature) {
				this.signature = signature;
				return this;
			}

			public ContentBlockBuilder thinking(String thinking) {
				this.thinking = thinking;
				return this;
			}

			public ContentBlockBuilder data(String data) {
				this.data = data;
				return this;
			}

			public ContentBlockBuilder cacheControl(CacheControl cacheControl) {
				this.cacheControl = cacheControl;
				return this;
			}

			public ContentBlockBuilder fileId(String fileId) {
				this.fileId = fileId;
				return this;
			}

			public ContentBlockBuilder filename(String filename) {
				this.filename = filename;
				return this;
			}

			public ContentBlock build() {
				return new ContentBlock(this.type, this.source, this.text, this.index, this.id, this.name, this.input,
						this.toolUseId, this.content, this.signature, this.thinking, this.data, this.cacheControl,
						this.title, this.context, this.citations, this.fileId, this.filename);
			}

		}
	}

	///////////////////////////////////////
	/// CONTENT_BLOCK EVENTS
	///////////////////////////////////////

	/**
	 * Tool description.
	 *
	 * @param type The type of the tool (e.g., "code_execution_20250825" for code
	 * execution).
	 * @param name The name of the tool.
	 * @param description A description of the tool.
	 * @param inputSchema The input schema of the tool.
	 * @param cacheControl Optional cache control for this tool.
	 */
	@JsonInclude(Include.NON_NULL)
	public record Tool(
	// @formatter:off
		@JsonProperty("type") String type,
		@JsonProperty("name") String name,
		@JsonProperty("description") String description,
		@JsonProperty("input_schema") Map<String, Object> inputSchema,
		@JsonProperty("cache_control") CacheControl cacheControl) {
		// @formatter:on

		/**
		 * Constructor for backward compatibility without type or cache control.
		 */
		public Tool(String name, String description, Map<String, Object> inputSchema) {
			this(null, name, description, inputSchema, null);
		}

	}

	/**
	 * Base interface for tool choice options.
	 */
	@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type",
			visible = true)
	@JsonSubTypes({ @JsonSubTypes.Type(value = ToolChoiceAuto.class, name = "auto"),
			@JsonSubTypes.Type(value = ToolChoiceAny.class, name = "any"),
			@JsonSubTypes.Type(value = ToolChoiceTool.class, name = "tool"),
			@JsonSubTypes.Type(value = ToolChoiceNone.class, name = "none") })
	public interface ToolChoice {

		@JsonProperty("type")
		String type();

	}

	/**
	 * Auto tool choice - the model will automatically decide whether to use tools.
	 *
	 * @param type The type of tool choice, always "auto".
	 * @param disableParallelToolUse Whether to disable parallel tool use. Defaults to
	 * false. If set to true, the model will output at most one tool use.
	 */
	@JsonInclude(Include.NON_NULL)
	public record ToolChoiceAuto(@JsonProperty("type") String type,
			@JsonProperty("disable_parallel_tool_use") Boolean disableParallelToolUse) implements ToolChoice {

		/**
		 * Create an auto tool choice with default settings.
		 */
		public ToolChoiceAuto() {
			this("auto", null);
		}

		/**
		 * Create an auto tool choice with specific parallel tool use setting.
		 * @param disableParallelToolUse Whether to disable parallel tool use.
		 */
		public ToolChoiceAuto(Boolean disableParallelToolUse) {
			this("auto", disableParallelToolUse);
		}

	}

	/**
	 * Any tool choice - the model will use any available tools.
	 *
	 * @param type The type of tool choice, always "any".
	 * @param disableParallelToolUse Whether to disable parallel tool use. Defaults to
	 * false. If set to true, the model will output exactly one tool use.
	 */
	@JsonInclude(Include.NON_NULL)
	public record ToolChoiceAny(@JsonProperty("type") String type,
			@JsonProperty("disable_parallel_tool_use") Boolean disableParallelToolUse) implements ToolChoice {

		/**
		 * Create an any tool choice with default settings.
		 */
		public ToolChoiceAny() {
			this("any", null);
		}

		/**
		 * Create an any tool choice with specific parallel tool use setting.
		 * @param disableParallelToolUse Whether to disable parallel tool use.
		 */
		public ToolChoiceAny(Boolean disableParallelToolUse) {
			this("any", disableParallelToolUse);
		}

	}

	/**
	 * Tool choice - the model will use the specified tool.
	 *
	 * @param type The type of tool choice, always "tool".
	 * @param name The name of the tool to use.
	 * @param disableParallelToolUse Whether to disable parallel tool use. Defaults to
	 * false. If set to true, the model will output exactly one tool use.
	 */
	@JsonInclude(Include.NON_NULL)
	public record ToolChoiceTool(@JsonProperty("type") String type, @JsonProperty("name") String name,
			@JsonProperty("disable_parallel_tool_use") Boolean disableParallelToolUse) implements ToolChoice {

		/**
		 * Create a tool choice for a specific tool.
		 * @param name The name of the tool to use.
		 */
		public ToolChoiceTool(String name) {
			this("tool", name, null);
		}

		/**
		 * Create a tool choice for a specific tool with parallel tool use setting.
		 * @param name The name of the tool to use.
		 * @param disableParallelToolUse Whether to disable parallel tool use.
		 */
		public ToolChoiceTool(String name, Boolean disableParallelToolUse) {
			this("tool", name, disableParallelToolUse);
		}

	}

	/**
	 * None tool choice - the model will not be allowed to use tools.
	 *
	 * @param type The type of tool choice, always "none".
	 */
	@JsonInclude(Include.NON_NULL)
	public record ToolChoiceNone(@JsonProperty("type") String type) implements ToolChoice {

		/**
		 * Create a none tool choice.
		 */
		public ToolChoiceNone() {
			this("none");
		}

	}

	// CB START EVENT

	/**
	 * Chat completion response object.
	 *
	 * @param id Unique object identifier. The format and length of IDs may change over
	 * time.
	 * @param type Object type. For Messages, this is always "message".
	 * @param role Conversational role of the generated message. This will always be
	 * "assistant".
	 * @param content Content generated by the model. This is an array of content blocks.
	 * @param model The model that handled the request.
	 * @param stopReason The reason the model stopped generating tokens. This will be one
	 * of "end_turn", "max_tokens", "stop_sequence", "tool_use", or "timeout".
	 * @param stopSequence Which custom stop sequence was generated, if any.
	 * @param usage Input and output token usage.
	 */
	@JsonInclude(Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record ChatCompletionResponse(
	// @formatter:off
		@JsonProperty("id") String id,
		@JsonProperty("type") String type,
		@JsonProperty("role") Role role,
		@JsonProperty("content") List<ContentBlock> content,
		@JsonProperty("model") String model,
		@JsonProperty("stop_reason") String stopReason,
		@JsonProperty("stop_sequence") String stopSequence,
		@JsonProperty("usage") Usage usage,
		@JsonProperty("container") Container container) {
		// @formatter:on

		/**
		 * Container information for Skills execution context. Contains container_id that
		 * can be reused in multi-turn conversations.
		 *
		 * @param id Container identifier (format: container_*)
		 */
		@JsonInclude(Include.NON_NULL)
		@JsonIgnoreProperties(ignoreUnknown = true)
		public record Container(@JsonProperty("id") String id) {
		}
	}

	// CB DELTA EVENT

	/**
	 * Usage statistics.
	 *
	 * @param inputTokens The number of input tokens which were used.
	 * @param outputTokens The number of output tokens which were used. completion).
	 */
	@JsonInclude(Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Usage(
	// @formatter:off
		@JsonProperty("input_tokens") Integer inputTokens,
		@JsonProperty("output_tokens") Integer outputTokens,
		@JsonProperty("cache_creation_input_tokens") Integer cacheCreationInputTokens,
		@JsonProperty("cache_read_input_tokens") Integer cacheReadInputTokens) {
		// @formatter:off
	}

	 /// ECB STOP

	/**
	 * Special event used to aggregate multiple tool use events into a single event with
	 * list of aggregated ContentBlockToolUse.
	*/
	public static class ToolUseAggregationEvent implements StreamEvent {

		private Integer index;

		private String id;

		private String name;

		private String partialJson = "";

		private List<ContentBlockStartEvent.ContentBlockToolUse> toolContentBlocks = new ArrayList<>();

		@Override
		public EventType type() {
			return EventType.TOOL_USE_AGGREGATE;
		}

		/**
		  * Get tool content blocks.
		  * @return The tool content blocks.
		*/
		public List<ContentBlockStartEvent.ContentBlockToolUse> getToolContentBlocks() {
			return this.toolContentBlocks;
		}

		/**
		  * Check if the event is empty.
		  * @return True if the event is empty, false otherwise.
		*/
		public boolean isEmpty() {
			return (this.index == null || this.id == null || this.name == null);
		}

		ToolUseAggregationEvent withIndex(Integer index) {
			this.index = index;
			return this;
		}

		ToolUseAggregationEvent withId(String id) {
			this.id = id;
			return this;
		}

		ToolUseAggregationEvent withName(String name) {
			this.name = name;
			return this;
		}

		ToolUseAggregationEvent appendPartialJson(String partialJson) {
			this.partialJson = this.partialJson + partialJson;
			return this;
		}

		void squashIntoContentBlock() {
			Map<String, Object> map = (StringUtils.hasText(this.partialJson))
					? ModelOptionsUtils.jsonToMap(this.partialJson) : Map.of();
			this.toolContentBlocks.add(new ContentBlockStartEvent.ContentBlockToolUse("tool_use", this.id, this.name, map));
			this.index = null;
			this.id = null;
			this.name = null;
			this.partialJson = "";
		}

		@Override
		public String toString() {
			return "EventToolUseBuilder [index=" + this.index + ", id=" + this.id + ", name=" + this.name + ", partialJson="
					+ this.partialJson + ", toolUseMap=" + this.toolContentBlocks + "]";
		}

	}

	 ///////////////////////////////////////
	 /// MESSAGE EVENTS
	 ///////////////////////////////////////

	 // MESSAGE START EVENT

	/**
	 * Content block start event.
	 * @param type The event type.
	 * @param index The index of the content block.
	 * @param contentBlock The content block body.
	*/
	@JsonInclude(Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record ContentBlockStartEvent(
			// @formatter:off
		@JsonProperty("type") EventType type,
		@JsonProperty("index") Integer index,
		@JsonProperty("content_block") ContentBlockBody contentBlock) implements StreamEvent {

		@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type",
				visible = true)
		@JsonSubTypes({
				@JsonSubTypes.Type(value = ContentBlockToolUse.class, name = "tool_use"),
				@JsonSubTypes.Type(value = ContentBlockText.class, name = "text"),
				@JsonSubTypes.Type(value = ContentBlockThinking.class, name = "thinking")
		})
		public interface ContentBlockBody {
			String type();
		}

		/**
		  * Tool use content block.
		  * @param type The content block type.
		  * @param id The tool use id.
		  * @param name The tool use name.
		  * @param input The tool use input.
		*/
		@JsonInclude(Include.NON_NULL)
		@JsonIgnoreProperties(ignoreUnknown = true)
		public record ContentBlockToolUse(
			@JsonProperty("type") String type,
			@JsonProperty("id") String id,
			@JsonProperty("name") String name,
			@JsonProperty("input") Map<String, Object> input) implements ContentBlockBody {
		}

		/**
		  * Text content block.
		  * @param type The content block type.
		  * @param text The text content.
		*/
		@JsonInclude(Include.NON_NULL)
		@JsonIgnoreProperties(ignoreUnknown = true)
		public record ContentBlockText(
			@JsonProperty("type") String type,
			@JsonProperty("text") String text) implements ContentBlockBody {
		}

		/**
		 * Thinking content block.
		 * @param type The content block type.
		 * @param thinking The thinking content.
		 */
		@JsonInclude(Include.NON_NULL)
		public record ContentBlockThinking(
			@JsonProperty("type") String type,
			@JsonProperty("thinking") String thinking,
			@JsonProperty("signature") String signature) implements ContentBlockBody {
		}
	}
	// @formatter:on

	// MESSAGE DELTA EVENT

	/**
	 * Content block delta event.
	 *
	 * @param type The event type.
	 * @param index The index of the content block.
	 * @param delta The content block delta body.
	 */
	@JsonInclude(Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record ContentBlockDeltaEvent(
	// @formatter:off
		@JsonProperty("type") EventType type,
		@JsonProperty("index") Integer index,
		@JsonProperty("delta") ContentBlockDeltaBody delta) implements StreamEvent {

		@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type",
				visible = true)
		@JsonSubTypes({ @JsonSubTypes.Type(value = ContentBlockDeltaText.class, name = "text_delta"),
				@JsonSubTypes.Type(value = ContentBlockDeltaJson.class, name = "input_json_delta"),
				@JsonSubTypes.Type(value = ContentBlockDeltaThinking.class, name = "thinking_delta"),
				@JsonSubTypes.Type(value = ContentBlockDeltaSignature.class, name = "signature_delta")
		})
		public interface ContentBlockDeltaBody {
			String type();
		}

		/**
		 * Text content block delta.
		 * @param type The content block type.
		 * @param text The text content.
		*/
		@JsonInclude(Include.NON_NULL)
		@JsonIgnoreProperties(ignoreUnknown = true)
		public record ContentBlockDeltaText(
			@JsonProperty("type") String type,
			@JsonProperty("text") String text) implements ContentBlockDeltaBody {
		}

		/**
		  * JSON content block delta.
		  * @param type The content block type.
		  * @param partialJson The partial JSON content.
		  */
		@JsonInclude(Include.NON_NULL)
		@JsonIgnoreProperties(ignoreUnknown = true)
		public record ContentBlockDeltaJson(
			@JsonProperty("type") String type,
			@JsonProperty("partial_json") String partialJson) implements ContentBlockDeltaBody {
		}

		/**
		 * Thinking content block delta.
		 * @param type The content block type.
		 * @param thinking The thinking content.
		 */
		@JsonInclude(Include.NON_NULL)
		@JsonIgnoreProperties(ignoreUnknown = true)
		public record ContentBlockDeltaThinking(
			@JsonProperty("type") String type,
			@JsonProperty("thinking") String thinking) implements ContentBlockDeltaBody {
		}

		/**
		 * Signature content block delta.
		 * @param type The content block type.
		 * @param signature The signature content.
		 */
		@JsonInclude(Include.NON_NULL)
		@JsonIgnoreProperties(ignoreUnknown = true)
		public record ContentBlockDeltaSignature(
			@JsonProperty("type") String type,
			@JsonProperty("signature") String signature) implements ContentBlockDeltaBody {
		}
	}
	// @formatter:on

	// MESSAGE STOP EVENT

	/**
	 * Content block stop event.
	 *
	 * @param type The event type.
	 * @param index The index of the content block.
	 */
	@JsonInclude(Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record ContentBlockStopEvent(
	// @formatter:off
		@JsonProperty("type") EventType type,
		@JsonProperty("index") Integer index) implements StreamEvent {
	}
	// @formatter:on

	/**
	 * Message start event.
	 *
	 * @param type The event type.
	 * @param message The message body.
	 */
	@JsonInclude(Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record MessageStartEvent(// @formatter:off
		@JsonProperty("type") EventType type,
		@JsonProperty("message") ChatCompletionResponse message) implements StreamEvent {
	}
	// @formatter:on

	/**
	 * Message delta event.
	 *
	 * @param type The event type.
	 * @param delta The message delta body.
	 * @param usage The message delta usage.
	 */
	@JsonInclude(Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record MessageDeltaEvent(
	// @formatter:off
		@JsonProperty("type") EventType type,
		@JsonProperty("delta") MessageDelta delta,
		@JsonProperty("usage") MessageDeltaUsage usage) implements StreamEvent {

		/**
		  * @param stopReason The stop reason.
		  * @param stopSequence The stop sequence.
		  */
		@JsonInclude(Include.NON_NULL)
		@JsonIgnoreProperties(ignoreUnknown = true)
		public record MessageDelta(
			@JsonProperty("stop_reason") String stopReason,
			@JsonProperty("stop_sequence") String stopSequence) {
		}

		/**
		 * Message delta usage.
		 * @param outputTokens The output tokens.
		*/
		@JsonInclude(Include.NON_NULL)
		@JsonIgnoreProperties(ignoreUnknown = true)
		public record MessageDeltaUsage(
			@JsonProperty("output_tokens") Integer outputTokens) {
		}
	}
	// @formatter:on

	/**
	 * Message stop event.
	 *
	 * @param type The event type.
	 */
	@JsonInclude(Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record MessageStopEvent(
	//@formatter:off
		@JsonProperty("type") EventType type) implements StreamEvent {
	}
	// @formatter:on

	///////////////////////////////////////
	/// ERROR EVENT
	///////////////////////////////////////
	/**
	 * Error event.
	 *
	 * @param type The event type.
	 * @param error The error body.
	 */
	@JsonInclude(Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record ErrorEvent(
	// @formatter:off
		@JsonProperty("type") EventType type,
		@JsonProperty("error") Error error) implements StreamEvent {

		/**
		 * Error body.
		 * @param type The error type.
		 * @param message The error message.
		*/
		@JsonInclude(Include.NON_NULL)
		@JsonIgnoreProperties(ignoreUnknown = true)
		public record Error(
			@JsonProperty("type") String type,
			@JsonProperty("message") String message) {
		}
	}
	// @formatter:on

	///////////////////////////////////////
	/// PING EVENT
	///////////////////////////////////////
	/**
	 * Ping event.
	 *
	 * @param type The event type.
	 */
	@JsonInclude(Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record PingEvent(
	// @formatter:off
		@JsonProperty("type") EventType type) implements StreamEvent {
	}
	// @formatter:on

	public static final class Builder {

		private String baseUrl = DEFAULT_BASE_URL;

		private String completionsPath = DEFAULT_MESSAGE_COMPLETIONS_PATH;

		private ApiKey apiKey;

		private String anthropicVersion = DEFAULT_ANTHROPIC_VERSION;

		private RestClient.Builder restClientBuilder = RestClient.builder();

		private WebClient.Builder webClientBuilder = WebClient.builder();

		private ResponseErrorHandler responseErrorHandler = RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER;

		private String anthropicBetaFeatures = DEFAULT_ANTHROPIC_BETA_VERSION;

		public Builder baseUrl(String baseUrl) {
			Assert.hasText(baseUrl, "baseUrl cannot be null or empty");
			this.baseUrl = baseUrl;
			return this;
		}

		public Builder completionsPath(String completionsPath) {
			Assert.hasText(completionsPath, "completionsPath cannot be null or empty");
			this.completionsPath = completionsPath;
			return this;
		}

		public Builder apiKey(ApiKey apiKey) {
			Assert.notNull(apiKey, "apiKey cannot be null");
			this.apiKey = apiKey;
			return this;
		}

		public Builder apiKey(String simpleApiKey) {
			Assert.notNull(simpleApiKey, "simpleApiKey cannot be null");
			this.apiKey = new SimpleApiKey(simpleApiKey);
			return this;
		}

		public Builder anthropicVersion(String anthropicVersion) {
			Assert.notNull(anthropicVersion, "anthropicVersion cannot be null");
			this.anthropicVersion = anthropicVersion;
			return this;
		}

		public Builder restClientBuilder(RestClient.Builder restClientBuilder) {
			Assert.notNull(restClientBuilder, "restClientBuilder cannot be null");
			this.restClientBuilder = restClientBuilder;
			return this;
		}

		public Builder webClientBuilder(WebClient.Builder webClientBuilder) {
			Assert.notNull(webClientBuilder, "webClientBuilder cannot be null");
			this.webClientBuilder = webClientBuilder;
			return this;
		}

		public Builder responseErrorHandler(ResponseErrorHandler responseErrorHandler) {
			Assert.notNull(responseErrorHandler, "responseErrorHandler cannot be null");
			this.responseErrorHandler = responseErrorHandler;
			return this;
		}

		public Builder anthropicBetaFeatures(String anthropicBetaFeatures) {
			Assert.notNull(anthropicBetaFeatures, "anthropicBetaFeatures cannot be null");
			this.anthropicBetaFeatures = anthropicBetaFeatures;
			return this;
		}

		public AnthropicApi build() {
			Assert.notNull(this.apiKey, "apiKey must be set");
			return new AnthropicApi(this.baseUrl, this.completionsPath, this.apiKey, this.anthropicVersion,
					this.restClientBuilder, this.webClientBuilder, this.responseErrorHandler,
					this.anthropicBetaFeatures);
		}

	}

}
