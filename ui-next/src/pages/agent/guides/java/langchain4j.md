## 1. Add dependencies

```groovy
implementation 'org.conductoross:conductor-ai:5.1.0'
compileOnly 'dev.langchain4j:langchain4j:1.0.0'
```

## 2. Wrap and run your tools

```java
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.conductoross.conductor.ai.Agent;
import org.conductoross.conductor.ai.AgentRuntime;
import org.conductoross.conductor.ai.frameworks.LangChain4jAgent;

class CalculatorTools {
    @Tool("Add two integers")
    public int add(@P("a") int a, @P("b") int b) {
        return a + b;
    }
}

Agent agent = LangChain4jAgent.from(
    "calculator", "openai/gpt-4o-mini", "Use the calculator tool.",
    new CalculatorTools());

try (AgentRuntime runtime = new AgentRuntime()) {
    runtime.run(agent, "What is 17 plus 25?").printResult();
}
```

Configure the server and provider key before running:

```bash
export CONDUCTOR_SERVER_URL={{CONDUCTOR_SERVER_URL}}
# For authenticated Conductor servers:
# export CONDUCTOR_AUTH_KEY=<YOUR_AUTH_KEY>
# export CONDUCTOR_AUTH_SECRET=<YOUR_AUTH_SECRET>
```

[LangChain4j bridge documentation](https://github.com/conductor-oss/java-sdk/blob/main/docs/agents/frameworks/langchain4j.md) · [Java examples](https://github.com/conductor-oss/java-sdk/tree/main/conductor-ai-examples)
