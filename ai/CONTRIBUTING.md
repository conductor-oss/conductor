# Contributing to Conductor AI Module

Thank you for your interest in contributing to the Conductor AI module! This guide will help you add new LLM providers, vector database integrations, workers, and other enhancements.

## Table of Contents

- [Architecture Overview](#architecture-overview)
- [Adding a New LLM Provider](#adding-a-new-llm-provider)
- [Adding a Vector Database Integration](#adding-a-vector-database-integration)
- [Adding New Workers/Tasks](#adding-new-workerstasks)
- [Adding MCP Tools](#adding-mcp-tools)
- [Testing Guidelines](#testing-guidelines)
- [Code Style and Best Practices](#code-style-and-best-practices)

---

## Architecture Overview

The AI module is organized into several key packages:

```
org.conductoross.conductor.ai/
├── providers/           # LLM provider implementations (OpenAI, Anthropic, etc.)
├── vectordb/           # Vector database integrations (Pinecone, MongoDB, etc.)
├── video/              # Video generation abstractions (VideoModel, AsyncVideoModel, etc.)
├── tasks/              # Worker task definitions
│   ├── mapper/         # Input/output parameter mappers
│   └── worker/         # Worker implementations
├── mcp/                # Model Context Protocol implementation
├── models/             # Request/response models
└── document/           # Document readers and parsers
```

Key interfaces:
- **`AIModel`**: Base interface for LLM providers
- **`VideoModel`**: Functional interface for synchronous video generation (mirrors Spring AI's `ImageModel`)
- **`AsyncVideoModel`**: Extends `VideoModel` with async polling via `checkStatus(String jobId)`
- **`VectorDBProvider`**: Base interface for vector databases
- **`@WorkerTask`**: Annotation for defining worker tasks

---

## Adding a New LLM Provider

### Step 1: Create Provider Package

Create a new package under `providers/`:

```
org.conductoross.conductor.ai.providers.yourprovider/
├── YourProvider.java          # Main provider implementation
└── YourProviderConfiguration.java  # Spring configuration
```

### Step 2: Implement AIModel Interface

Create your provider class implementing `AIModel`:

```java
package org.conductoross.conductor.ai.providers.yourprovider;

import org.conductoross.conductor.ai.AIModel;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;

public class YourProvider implements AIModel {
    
    private final ChatModel chatModel;
    private final EmbeddingModel embeddingModel;
    
    public YourProvider(ChatModel chatModel, EmbeddingModel embeddingModel) {
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
    }
    
    @Override
    public String getModelProvider() {
        return "your_provider_name";  // Used in workflow definitions
    }
    
    @Override
    public ChatModel getChatModel() {
        return chatModel;
    }
    
    @Override
    public EmbeddingModel getEmbeddingModel() {
        return embeddingModel;
    }
}
```

### Step 3: Create Configuration Class

Use `@ConditionalOnProperty` to ensure the provider only loads when configured:

```java
package org.conductoross.conductor.ai.providers.yourprovider;

import org.conductoross.conductor.ai.ModelConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(YourProviderProperties.class)
@ConditionalOnProperty(prefix = "conductor.ai.your-provider", name = "api-key")
public class YourProviderConfiguration {
    
    @Bean
    public ModelConfiguration<YourProvider> yourProviderConfiguration(
            YourProviderProperties properties) {
        return () -> {
            // Initialize chat and embedding models
            ChatModel chatModel = // ... create from properties
            EmbeddingModel embeddingModel = // ... create from properties
            
            return new YourProvider(chatModel, embeddingModel);
        };
    }
}
```

### Step 4: Create Properties Class

```java
package org.conductoross.conductor.ai.providers.yourprovider;

import org.springframework.boot.context.properties.ConfigurationProperties;
import lombok.Data;

@Data
@ConfigurationProperties(prefix = "conductor.ai.your-provider")
public class YourProviderProperties {
    private String apiKey;
    private String baseUrl = "https://api.yourprovider.com";
    private String model = "default-model";
    // Add other configuration properties
}
```

### Step 5: Add Tests

Create `YourProviderConfigurationTest.java`:

```java
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class YourProviderConfigurationTest {
    
    @Test
    void testProviderLoadsWhenConfigured() {
        ApplicationContextRunner contextRunner =
                new ApplicationContextRunner()
                        .withConfiguration(
                                AutoConfigurations.of(YourProviderConfiguration.class))
                        .withPropertyValues(
                                "conductor.ai.your-provider.api-key=test-key");
        
        contextRunner.run(
                context -> {
                    assertThat(context).hasSingleBean(ModelConfiguration.class);
                });
    }
    
    @Test
    void testProviderDoesNotLoadWithoutApiKey() {
        ApplicationContextRunner contextRunner =
                new ApplicationContextRunner()
                        .withConfiguration(
                                AutoConfigurations.of(YourProviderConfiguration.class));
        
        contextRunner.run(
                context -> {
                    assertThat(context).doesNotHaveBean(ModelConfiguration.class);
                });
    }
}
```

### Step 6: Add Video Generation Support (Optional)

If your provider supports video generation, implement video model support using the `video/` package abstractions. Video generation is async by nature (submit a job, poll for results), so most providers will implement `AsyncVideoModel`.

#### 6a. Create a Video Model Class

```java
package org.conductoross.conductor.ai.providers.yourprovider;

import org.conductoross.conductor.ai.video.*;

public class YourVideoModel implements AsyncVideoModel {

    private final String apiKey;

    public YourVideoModel(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public VideoResponse call(VideoPrompt prompt) {
        // Submit video generation job to provider API
        // Return a VideoResponse with jobId in metadata
        VideoResponseMetadata metadata = new VideoResponseMetadata();
        metadata.put("jobId", submittedJobId);
        metadata.put("status", "PENDING");
        return new VideoResponse(List.of(), metadata);
    }

    @Override
    public VideoResponse checkStatus(String jobId) {
        // Poll provider API for job status
        // When complete, download video bytes and return Video objects
        // Set mimeType on each Video (e.g., "video/mp4", "image/webp" for thumbnails)
        Video video = new Video(videoUrl, null, "video/mp4");
        VideoGeneration generation = new VideoGeneration(video);

        VideoResponseMetadata metadata = new VideoResponseMetadata();
        metadata.put("jobId", jobId);
        metadata.put("status", "COMPLETED");
        return new VideoResponse(List.of(generation), metadata);
    }
}
```

#### 6b. Wire Video Model into Your Provider

Override the video-related methods in your `AIModel` implementation:

```java
@Override
public VideoModel getVideoModel() {
    if (videoModel == null) {
        videoModel = new YourVideoModel(apiKey);
    }
    return videoModel;
}

@Override
public LLMResponse generateVideo(VideoGenRequest request) {
    VideoOptions options = getVideoOptions(request);
    VideoPrompt prompt = new VideoPrompt(
        List.of(new VideoMessage(request.getPrompt())), options);
    VideoResponse response = getVideoModel().call(prompt);
    // Convert to LLMResponse with jobId
}

@Override
public LLMResponse checkVideoStatus(VideoGenRequest request) {
    AsyncVideoModel asyncModel = (AsyncVideoModel) getVideoModel();
    VideoResponse response = asyncModel.checkStatus(request.getJobId());
    // Convert to LLMResponse with media list
}
```

The `video/` package mirrors Spring AI's `Image*` abstraction pattern:
- `VideoPrompt` -> `ImagePrompt` (request wrapper)
- `VideoResponse` -> `ImageResponse` (response wrapper)
- `VideoGeneration` -> `ImageGeneration` (individual result)
- `Video` -> `Image` (the actual media, with url, b64Json, and mimeType fields)
- `VideoOptions` -> `ImageOptions` (generation parameters)

### Step 7: Update Documentation

Add your provider to `README.md` under the supported providers section with configuration examples.

---

## Adding a Vector Database Integration

### Step 1: Create Config Class

Create a new configuration class in the database package (e.g., `org.conductoross.conductor.ai.vectordb.yourdb`):

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class YourDBConfig implements VectorDBConfig<YourVectorDB> {
    
    private String connectionString;
    // other properties
    
    @Override
    public YourVectorDB get() {
        throw new UnsupportedOperationException("Use get(String name) instead");
    }

    public YourVectorDB get(String name) {
        return new YourVectorDB(name, this);
    }
}
```

### Step 2: Implement VectorDB Class

Extend the `VectorDB` abstract class:

```java
public class YourVectorDB extends VectorDB {
    
    public static final String TYPE = "yourdb";
    private final YourDBConfig config;
    
    public YourVectorDB(String name, YourDBConfig config) {
        super(name, TYPE);
        this.config = config;
    }
    
    @Override
    public int updateEmbeddings(String indexName, String namespace, String doc, String parentDocId, String id, List<Float> embeddings, Map<String, Object> metadata) {
        // Implement logic to store embeddings
    }
    
    @Override
    public List<IndexedDoc> search(String indexName, String namespace, List<Float> embeddings, int maxResults) {
        // Implement logic to search embeddings
    }
}
```

### Step 3: Register in VectorDBInstanceConfig

Add your database type to the `createVectorDB` method and the `VectorDBInstance` inner class in `org.conductoross.conductor.ai.vectordb.VectorDBInstanceConfig`.

### Step 4: Add Integration Tests

Use Testcontainers for integration testing:

```java
@Testcontainers
class YourVectorDBTest {
    
    @Container
    static GenericContainer<?> yourdb =
            new GenericContainer<>("yourdb:latest")
                    .withExposedPorts(1234);
    
    @Test
    void testStoreAndSearch() {
        // Test vector storage and similarity search
    }
}
```

---

## Adding New Workers/Tasks

### Step 1: Create Request Model

```java
package org.conductoross.conductor.ai.models;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class YourTaskRequest extends LLMWorkerInput {
    private String parameter1;
    private String parameter2;
    // Add task-specific parameters
}
```

### Step 2: Create Worker Class

```java
package org.conductoross.conductor.ai.tasks.worker;

import com.netflix.conductor.sdk.workflow.annotations.WorkerTask;
import org.conductoross.conductor.ai.models.YourTaskRequest;

@Component
public class YourWorker {
    
    private final YourService yourService;
    
    public YourWorker(YourService yourService) {
        this.yourService = yourService;
    }
    
    @WorkerTask("YOUR_TASK_NAME")
    public @OutputParam("result") YourTaskResult executeTask(YourTaskRequest request) {
        // Implement task logic
        return yourService.processRequest(request);
    }
}
```

### Step 3: Add Task Tests

```java
class YourWorkerTest {
    
    @Test
    void testTaskExecution() {
        YourWorker worker = new YourWorker(mockService);
        YourTaskRequest request = new YourTaskRequest();
        request.setParameter1("test");
        
        YourTaskResult result = worker.executeTask(request);
        
        assertNotNull(result);
        // Add assertions
    }
}
```

---

## Adding MCP Tools

Model Context Protocol (MCP) allows external tools to be called from workflows.

### Adding MCP Server Support

The `MCPService` already supports:
- HTTP/SSE transports
- stdio (local process) transports
- Direct JSON-RPC fallback

To add a new MCP server:

1. **Deploy your MCP server** (HTTP or local script)
2. **Use existing `CALL_MCP_TOOL` task** in workflows:

```json
{
  "name": "call_your_tool",
  "taskReferenceName": "your_tool",
  "type": "CALL_MCP_TOOL",
  "inputParameters": {
    "mcpServer": "http://localhost:3000",
    "methodName": "your_tool_name",
    "param1": "value1",
    "param2": "value2"
  }
}
```

### Extending MCP Capabilities

To add new MCP-related features, modify:
- `MCPService.java` - Core MCP communication logic
- `MCPWorkers.java` - Worker task definitions
- `models/MCP*.java` - Request/response models

---

## Testing Guidelines

### Unit Tests

- Place in `src/test/java` mirroring the source structure
- Use MockBean for Spring dependencies
- Test individual methods and edge cases
- Aim for 80%+ code coverage

### Integration Tests

- Use `@SpringBootTest` for full context testing
- Use Testcontainers for external dependencies (databases, servers)
- Test real interactions between components

### Test Naming Convention

```java
// Unit test method format
void test<MethodName>_<Scenario>_<ExpectedResult>()

// Examples:
void testGetModel_WithValidProvider_ReturnsModel()
void testGetModel_WithInvalidProvider_ThrowsException()
```

### Running Tests

```bash
# Run all tests
./gradlew :conductor-ai:test

# Run specific test class
./gradlew :conductor-ai:test --tests YourProviderTest

# Run with coverage
./gradlew :conductor-ai:test jacocoTestReport
```

---

## Code Style and Best Practices

### Lombok Usage

Use Lombok annotations consistently:
- `@Data` for simple POJOs
- `@Builder` for complex object construction
- `@Slf4j` for logging
- `@AllArgsConstructor` / `@NoArgsConstructor` for constructors

### Logging

- Use SLF4J via `@Slf4j`
- Log levels:
  - `log.debug()` - Detailed diagnostic information
  - `log.info()` - Important business events
  - `log.warn()` - Recoverable issues
  - `log.error()` - Errors requiring attention

### Error Handling

- Throw descriptive exceptions
- Include context in error messages
- Use try-catch for recoverable errors
- Let unchecked exceptions propagate for programming errors

### Configuration Properties

- Use `@ConfigurationProperties` for type-safe configuration
- Provide sensible defaults
- Document all properties in javadoc
- Use `@ConditionalOnProperty` to make features optional

### Spring Beans

- Prefer constructor injection over field injection
- Use `@Component` for auto-detected beans
- Use `@Configuration` for explicit bean definitions
- Apply `@ConditionalOnProperty` for optional features

### Documentation

- Add Javadoc to all public classes and methods
- Include usage examples in class-level Javadoc
- Update `README.md` with new features
- Provide workflow examples for new tasks

---

## Development Workflow

### 1. Create a Feature Branch

```bash
git checkout -b feature/add-your-provider
```

### 2. Implement Your Changes

Follow the patterns above for your contribution type.

### 3. Write Tests

Ensure your code has comprehensive test coverage.

### 4. Run Tests and Checks

```bash
./gradlew :conductor-ai:test
./gradlew :conductor-ai:compileJava
```

### 5. Update Documentation

- Update `README.md` with examples
- Add Javadoc to new classes
- Update this CONTRIBUTING.md if adding new patterns

### 6. Submit Pull Request

- Provide clear description of changes
- Reference any related issues
- Include test results
- Update changelog if applicable

---

## Common Patterns

### Conditional Bean Creation

Always use `@ConditionalOnProperty` for optional integrations:

```java
@ConditionalOnProperty(
    prefix = "conductor.ai.your-feature",
    name = "enabled",
    havingValue = "true"
)
```

### Parameter Mapping

For workers with dynamic parameters, use `@JsonAnySetter`:

```java
@JsonAnySetter
public void setAdditionalProperty(String key, Object value) {
    additionalProperties.put(key, value);
}
```

### Resource Cleanup

Implement `DisposableBean` for cleanup:

```java
@Override
public void destroy() throws Exception {
    // Clean up resources
}
```

---

## Getting Help

- Check existing implementations in `providers/` for examples
- Review `README.md` for usage patterns
- Look at test files for testing patterns
- Open a GitHub issue for questions

## License

By contributing, you agree that your contributions will be licensed under the Apache License 2.0.
