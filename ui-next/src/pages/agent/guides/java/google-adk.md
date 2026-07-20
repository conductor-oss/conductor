## 1. Add dependencies

```groovy
implementation 'org.conductoross:conductor-ai:5.1.0'
compileOnly 'com.google.adk:google-adk:1.3.0'
```

## 2. Configure and run

```bash
export CONDUCTOR_SERVER_URL={{CONDUCTOR_SERVER_URL}}
# For authenticated Conductor servers:
# export CONDUCTOR_AUTH_KEY=<YOUR_AUTH_KEY>
# export CONDUCTOR_AUTH_SECRET=<YOUR_AUTH_SECRET>
export AGENTSPAN_LLM_MODEL=google_gemini/gemini-2.0-flash
```

```java
import com.google.adk.agents.LlmAgent;
import org.conductoross.conductor.ai.Agent;
import org.conductoross.conductor.ai.AgentRuntime;
import org.conductoross.conductor.ai.frameworks.AdkBridge;

LlmAgent adkAgent = LlmAgent.builder()
    .name("adk_greeter")
    .model("gemini-2.0-flash")
    .instruction("You are friendly and concise.")
    .build();

Agent agent = AdkBridge.toAgentspan(adkAgent);
try (AgentRuntime runtime = new AgentRuntime()) {
    runtime.run(agent, "Share a machine learning fact.").printResult();
}
```

[Google ADK bridge documentation](https://github.com/conductor-oss/java-sdk/blob/main/docs/agents/frameworks/google-adk.md) · [Java examples](https://github.com/conductor-oss/java-sdk/tree/main/conductor-ai-examples)
