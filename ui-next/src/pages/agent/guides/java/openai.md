## 1. Add dependencies

```groovy
implementation 'org.conductoross:conductor-ai:5.1.0'
compileOnly 'dev.langchain4j:langchain4j:1.0.0'
```

## 2. Configure and run

```bash
export CONDUCTOR_SERVER_URL={{CONDUCTOR_SERVER_URL}}
# For authenticated Conductor servers:
# export CONDUCTOR_AUTH_KEY=<YOUR_AUTH_KEY>
# export CONDUCTOR_AUTH_SECRET=<YOUR_AUTH_SECRET>
export AGENTSPAN_LLM_MODEL=openai/gpt-4o-mini
```

```java
import org.conductoross.conductor.ai.Agent;
import org.conductoross.conductor.ai.AgentRuntime;
import org.conductoross.conductor.ai.frameworks.OpenAIAgent;

Agent agent = OpenAIAgent.builder()
    .name("openai_style_greeter")
    .model("openai/gpt-4o-mini")
    .instructions("You are friendly and concise.")
    .build();

try (AgentRuntime runtime = new AgentRuntime()) {
    runtime.run(agent, "Share a durable execution fact.").printResult();
}
```

[OpenAI bridge documentation](https://github.com/conductor-oss/java-sdk/blob/main/docs/agents/frameworks/openai.md) · [Java examples](https://github.com/conductor-oss/java-sdk/tree/main/conductor-ai-examples)
