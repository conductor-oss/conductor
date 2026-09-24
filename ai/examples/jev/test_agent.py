import json
import os
import pickle
import unittest

from conductor.ai.agents import AgentRuntime
from conductor.ai.agents.config_serializer import AgentConfigSerializer

from jev_agent import create_agent, configuration
from test_jev import JevFixture


class AgentTest(JevFixture):
    def test_decorators_bind_tool_without_exposing_credentials(self):
        definition = create_agent("openai/configured-model", self.client)
        serialized = AgentConfigSerializer().serialize(definition)
        self.assertFalse(serialized["external"])
        self.assertEqual(3, serialized["maxTurns"])
        self.assertEqual("jev_decide", serialized["tools"][0]["name"])
        self.assertEqual({"state", "questions"}, set(
            serialized["tools"][0]["inputSchema"]["properties"]))
        self.assertNotIn("test-only-key", json.dumps(serialized))
        self.assertEqual(0, definition.tools[0].retry_count)
        self.assertEqual(1, definition.tools[0].max_calls)

    def test_bound_tool_survives_worker_serialization_and_calls_jev(self):
        definition = create_agent("openai/configured-model", self.client)
        # Spawned workers serialize their callable. Exercise the rebound method,
        # including its provider configuration, against a real loopback server.
        call = pickle.loads(pickle.dumps(definition.tools[0].func))
        result = call(state=self.data["state"], questions=self.data["questions"])
        self.assertEqual("billing", result["answers"]["team"]["choice"])
        self.assertEqual(1, len(self.calls))

    def test_tool_failure_is_sanitized(self):
        self.status, self.response = 401, {"error": "private-provider-body"}
        definition = create_agent("openai/configured-model", self.client)
        with self.assertRaisesRegex(ValueError, "^Jev decision failed: http_status_401$"):
            definition.tools[0].func(state=self.data["state"], questions=self.data["questions"])

    def test_agent_model_configuration_is_independent(self):
        first = create_agent("openai/first", self.client)
        second = create_agent("other/second", self.client)
        self.assertEqual("openai/first", first.model)
        self.assertEqual("other/second", second.model)

    @unittest.skipUnless(os.environ.get("CONDUCTOR_AGENT_TEST_MODEL"),
                         "Set CONDUCTOR_AGENT_TEST_MODEL for a paid orchestration test")
    def test_live_agent_runtime_calls_jev_tool(self):
        definition = create_agent(os.environ["CONDUCTOR_AGENT_TEST_MODEL"], self.client)
        with AgentRuntime(configuration()) as runtime:
            plan = runtime.plan(definition)
            self.assertEqual("jev_decision_agent", plan["workflowDef"]["name"])
            result = runtime.run(definition, json.dumps({
                "state": self.data["state"], "questions": self.data["questions"],
            }), timeout=120)
        self.assertTrue(result.is_success, result.status)
        self.assertEqual(1, len(self.calls))
        self.assertEqual(self.data, self.calls[0][1])
        self.assertIn("billing", json.dumps(result.output).lower())


if __name__ == "__main__":
    unittest.main()
