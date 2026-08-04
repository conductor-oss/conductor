## 1. Add dependencies

```groovy
implementation 'org.conductoross:conductor-ai:5.1.0'
compileOnly 'dev.langchain4j:langchain4j-open-ai:1.0.0'
compileOnly 'org.bsc.langgraph4j:langgraph4j-agent-executor:1.6.0-beta5'
```

## 2. Run the builder

```java
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.bsc.langgraph4j.agentexecutor.AgentExecutor;
import org.conductoross.conductor.ai.AgentRuntime;

ChatModel model = OpenAiChatModel.builder()
    .apiKey("conductor-server-handles-credentials")
    .modelName("gpt-4o-mini")
    .build();

AgentExecutor.Builder agent = AgentExecutor.builder().chatModel(model);
try (AgentRuntime runtime = new AgentRuntime()) {
    runtime.run(agent, "Share a state-machine fact.").printResult();
}
```

Configure the server and provider key before running:

```bash
export CONDUCTOR_SERVER_URL={{CONDUCTOR_SERVER_URL}}
# For authenticated Conductor servers:
# export CONDUCTOR_AUTH_KEY=<YOUR_AUTH_KEY>
# export CONDUCTOR_AUTH_SECRET=<YOUR_AUTH_SECRET>
```

[LangGraph4j bridge documentation](https://github.com/conductor-oss/java-sdk/blob/main/docs/agents/frameworks/langgraph4j.md) · [Java examples](https://github.com/conductor-oss/java-sdk/tree/main/conductor-ai-examples)
