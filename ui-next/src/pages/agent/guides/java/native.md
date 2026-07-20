## 1. Add the SDK

```groovy
dependencies {
    implementation 'org.conductoross:conductor-ai:5.1.0'
}
```

## 2. Configure

```bash
export CONDUCTOR_SERVER_URL={{CONDUCTOR_SERVER_URL}}
# For authenticated Conductor servers:
# export CONDUCTOR_AUTH_KEY=<YOUR_AUTH_KEY>
# export CONDUCTOR_AUTH_SECRET=<YOUR_AUTH_SECRET>
export AGENTSPAN_LLM_MODEL=openai/gpt-4o-mini
```

## 3. Run an agent

```java
import org.conductoross.conductor.ai.Agent;
import org.conductoross.conductor.ai.AgentRuntime;
import org.conductoross.conductor.ai.model.AgentResult;

Agent agent = Agent.builder()
    .name("java_greeter")
    .model("openai/gpt-4o-mini")
    .instructions("You are friendly and concise.")
    .build();

try (AgentRuntime runtime = new AgentRuntime()) {
    AgentResult result = runtime.run(agent, "Share a fun Java fact.");
    result.printResult();
}
```

Run the class with your project’s normal Gradle or Maven application task, then return to [Agents](/agents).

[Runnable Java examples](https://github.com/conductor-oss/java-sdk/tree/main/conductor-ai-examples) · [Java agent documentation](https://github.com/conductor-oss/java-sdk/tree/main/docs/agents)
